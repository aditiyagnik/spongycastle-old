package org.bouncycastle.asn1;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;

class EOCObject 
    extends DERObject
{
    void encode(
        DEROutputStream out)
    throws IOException
    {
        throw new IOException("Eeek!");
    }
    
    public boolean equals(Object o)
    {
        return (o instanceof EOCObject);
    }
    
    public int hashCode()
    {
        return 0;
    }
}

/**
 * a general purpose ASN.1 decoder - note: this class differs from the
 * others in that it returns null after it has read the last object in
 * the stream. If an ASN.1 NULL is encountered a DER/BER Null object is
 * returned.
 */
public class ASN1InputStream
    extends DERInputStream
{
    private static final DERObject END_OF_STREAM = new EOCObject();

    public ASN1InputStream(
        InputStream is)
    {
        super(is);
    }

    protected int readLength()
        throws IOException
    {
        int length = read();
        if (length < 0)
        {
            throw new IOException("EOF found when length expected");
        }

        if (length == 0x80)
        {
            return -1;      // indefinite-length encoding
        }

        if (length > 127)
        {
            int size = length & 0x7f;

            if (size > 4)
            {
                throw new IOException("DER length more than 4 bytes");
            }
            
            length = 0;
            for (int i = 0; i < size; i++)
            {
                int next = read();

                if (next < 0)
                {
                    throw new IOException("EOF found reading length");
                }

                length = (length << 8) + next;
            }
            
            if (length < 0)
            {
                throw new IOException("corrupted stream - negative length found");
            }
        }

        return length;
    }

    protected void readFully(
        byte[]  bytes)
        throws IOException
    {
        int     left = bytes.length;

        if (left == 0)
        {
            return;
        }

        while ((left -= read(bytes, bytes.length - left, left)) != 0)
        {
            ;
        }
    }

    /**
     * build an object given its tag and a byte stream to construct it
     * from.
     */
    protected DERObject buildObject(
        int        tag,
        byte[]    bytes)
        throws IOException
    {
        switch (tag)
        {
        case NULL:
            return new DERNull();   
        case SEQUENCE | CONSTRUCTED:
            ByteArrayInputStream    bIn = new ByteArrayInputStream(bytes);
            ASN1InputStream         aIn = new ASN1InputStream(bIn);
            ASN1EncodableVector     v = new ASN1EncodableVector();

            DERObject   obj = aIn.readObject();

            while (obj != null)
            {
                v.add(obj);
                obj = aIn.readObject();
            }

            return new DERSequence(v);
        case SET | CONSTRUCTED:
            bIn = new ByteArrayInputStream(bytes);
            aIn = new ASN1InputStream(bIn);
            v = new ASN1EncodableVector();

            obj = aIn.readObject();

            while (obj != null)
            {
                v.add(obj);
                obj = aIn.readObject();
            }

            return new DERSet(v);
        case BOOLEAN:
            return new DERBoolean(bytes);
        case INTEGER:
            return new DERInteger(bytes);
        case ENUMERATED:
            return new DEREnumerated(bytes);
        case OBJECT_IDENTIFIER:
            return new DERObjectIdentifier(bytes);
        case BIT_STRING:
            int     padBits = bytes[0];
            byte[]  data = new byte[bytes.length - 1];

            System.arraycopy(bytes, 1, data, 0, bytes.length - 1);

            return new DERBitString(data, padBits);
        case UTF8_STRING:
            return new DERUTF8String(bytes);
        case PRINTABLE_STRING:
            return new DERPrintableString(bytes);
        case IA5_STRING:
            return new DERIA5String(bytes);
        case T61_STRING:
            return new DERT61String(bytes);
        case VISIBLE_STRING:
            return new DERVisibleString(bytes);
        case UNIVERSAL_STRING:
            return new DERUniversalString(bytes);
        case BMP_STRING:
            return new DERBMPString(bytes);
        case OCTET_STRING:
            return new DEROctetString(bytes);
        case UTC_TIME:
            return new DERUTCTime(bytes);
        case GENERALIZED_TIME:
            return new DERGeneralizedTime(bytes);
        default:
            //
            // with tagged object tag number is bottom 5 bits
            //
            if ((tag & TAGGED) != 0)  
            {
                if ((tag & 0x1f) == 0x1f)
                {
                    throw new IOException("unsupported high tag encountered");
                }

                if (bytes.length == 0)        // empty tag!
                {
                    return new DERTaggedObject(tag & 0x1f);
                }

                //
                // simple type - implicit... return an octet string
                //
                if ((tag & CONSTRUCTED) == 0)
                {
                    return new DERTaggedObject(false, tag & 0x1f, new DEROctetString(bytes));
                }

                bIn = new ByteArrayInputStream(bytes);
                aIn = new ASN1InputStream(bIn);

                DEREncodable dObj = aIn.readObject();

                //
                // explicitly tagged (probably!) - if it isn't we'd have to
                // tell from the context
                //
                if (aIn.available() == 0)
                {
                    return new DERTaggedObject(tag & 0x1f, dObj);
                }

                //
                // another implicit object, we'll create a sequence...
                //
                v = new ASN1EncodableVector();

                while (dObj != null)
                {
                    v.add(dObj);
                    dObj = aIn.readObject();
                }

                return new DERTaggedObject(false, tag & 0x1f, new DERSequence(v));
            }

            return new DERUnknownTag(tag, bytes);
        }
    }

    /**
     * read a string of bytes representing an indefinite length object.
     */
    private byte[] readIndefiniteLengthFully()
        throws IOException
    {
        ByteArrayOutputStream   bOut = new ByteArrayOutputStream();
        int                     b, b1;

        b1 = read();

        while ((b = read()) >= 0)
        {
            if (b1 == 0 && b == 0)
            {
                break;
            }

            bOut.write(b1);
            b1 = b;
        }

        return bOut.toByteArray();
    }

    private BERConstructedOctetString buildConstructedOctetString()
        throws IOException
    {
        Vector                  octs = new Vector();

        for (;;)
        {
            DERObject        o = readObject();

            if (o == END_OF_STREAM)
            {
                break;
            }

            octs.addElement(o);
        }

        return new BERConstructedOctetString(octs);
    }

    public DERObject readObject()
        throws IOException
    {
        int tag = read();
        if (tag == -1)
        {
            return null;
        }
    
        int     length = readLength();

        if (length < 0)    // indefinite length method
        {
            switch (tag)
            {
            case NULL:
                return new BERNull();
            case SEQUENCE | CONSTRUCTED:
                ASN1EncodableVector  v = new ASN1EncodableVector();
    
                for (;;)
                {
                    DERObject   obj = readObject();

                    if (obj == END_OF_STREAM)
                    {
                        break;
                    }

                    v.add(obj);
                }
                return new BERSequence(v);
            case SET | CONSTRUCTED:
                v = new ASN1EncodableVector();
    
                for (;;)
                {
                    DERObject   obj = readObject();

                    if (obj == END_OF_STREAM)
                    {
                        break;
                    }

                    v.add(obj);
                }
                return new BERSet(v);
            case OCTET_STRING | CONSTRUCTED:
                return buildConstructedOctetString();
            default:
                //
                // with tagged object tag number is bottom 5 bits
                //
                if ((tag & TAGGED) != 0)  
                {
                    if ((tag & 0x1f) == 0x1f)
                    {
                        throw new IOException("unsupported high tag encountered");
                    }

                    //
                    // simple type - implicit... return an octet string
                    //
                    if ((tag & CONSTRUCTED) == 0)
                    {
                        byte[]  bytes = readIndefiniteLengthFully();

                        return new BERTaggedObject(false, tag & 0x1f, new DEROctetString(bytes));
                    }

                    //
                    // either constructed or explicitly tagged
                    //
                    DERObject        dObj = readObject();

                    if (dObj == END_OF_STREAM)     // empty tag!
                    {
                        return new DERTaggedObject(tag & 0x1f);
                    }

                    DERObject       next = readObject();

                    //
                    // explicitly tagged (probably!) - if it isn't we'd have to
                    // tell from the context
                    //
                    if (next == END_OF_STREAM)
                    {
                        return new BERTaggedObject(tag & 0x1f, dObj);
                    }

                    //
                    // another implicit object, we'll create a sequence...
                    //
                    v = new ASN1EncodableVector();

                    v.add(dObj);

                    do
                    {
                        v.add(next);
                        next = readObject();
                    }
                    while (next != END_OF_STREAM);

                    return new BERTaggedObject(false, tag & 0x1f, new BERSequence(v));
                }

                throw new IOException("unknown BER object encountered");
            }
        }
        else
        {
            if (tag == 0 && length == 0)    // end of contents marker.
            {
                return END_OF_STREAM;
            }

            byte[]  bytes = new byte[length];
    
            readFully(bytes);
    
            return buildObject(tag, bytes);
        }
    }
}