package org.bouncycastle.cert.cmp;

import org.bouncycastle.asn1.cmp.CertStatus;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.operator.ContentDigester;
import org.bouncycastle.operator.ContentDigesterProvider;
import org.bouncycastle.operator.DigestAlgorithmIdentifierFinder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.util.Arrays;

public class CertificateStatus
{
    private DigestAlgorithmIdentifierFinder digestAlgFinder;    
    private CertStatus certStatus;

    CertificateStatus(DigestAlgorithmIdentifierFinder digestAlgFinder, CertStatus certStatus)
    {
        this.digestAlgFinder = digestAlgFinder;
        this.certStatus = certStatus;
    }

    public boolean verify(X509CertificateHolder certHolder, ContentDigesterProvider digesterProvider)
        throws CMPException
    {
        AlgorithmIdentifier digAlg = digestAlgFinder.find(certHolder.toASN1Structure().getSignatureAlgorithm());
        if (digAlg == null)
        {
            throw new CMPException("cannot find algorithm for digest from signature");
        }

        ContentDigester digester;

        try
        {
            digester = digesterProvider.get(digAlg);
        }
        catch (OperatorCreationException e)
        {
            throw new CMPException("unable to create digester: " + e.getMessage(), e);
        }

        CMPUtil.derEncodeToStream(certHolder.toASN1Structure(), digester.getOutputStream());

        return Arrays.areEqual(certStatus.getCertHash().getOctets(), digester.getDigest());
    }
}
