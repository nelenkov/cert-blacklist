package org.nick.certblacklist;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.CertPathValidator;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.security.KeyChain;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements OnClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private static final String CA_CERT_FILENAME = "cacert.cer";;
    private static final String EE_CERT_FILENAME = "keystore-test.cer";

    public static final String PUBKEY_BLACKLIST_KEY = "pubkey_blacklist";
    public static final String SERIAL_BLACKLIST_KEY = "serial_blacklist";

    private TextView certInfoText;
    private TextView messageText;

    private Button installButtion;
    private Button verifyButton;
    private Button blacklistCaCertButton;
    private Button blacklistEeCertButton;
    private Button clearButton;

    private byte[] caCertBytes;
    private X509Certificate caCert;
    private X509Certificate eeCert;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        certInfoText = (TextView) findViewById(R.id.cert_info_text);
        messageText = (TextView) findViewById(R.id.message_text);

        installButtion = (Button) findViewById(R.id.install_button);
        installButtion.setOnClickListener(this);

        verifyButton = (Button) findViewById(R.id.verify_button);
        verifyButton.setOnClickListener(this);

        blacklistCaCertButton = (Button) findViewById(R.id.blacklist_ca_cert_button);
        blacklistCaCertButton.setOnClickListener(this);

        blacklistEeCertButton = (Button) findViewById(R.id.blacklist_ee_cert_button);
        blacklistEeCertButton.setOnClickListener(this);

        clearButton = (Button) findViewById(R.id.clear_button);
        clearButton.setOnClickListener(this);

        new AsyncTask<Void, Void, Boolean>() {
            Exception error;

            @Override
            protected void onPreExecute() {
                setProgressBarIndeterminateVisibility(true);
            }

            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    caCertBytes = readFile(CA_CERT_FILENAME);
                    caCert = readX509Cert(CA_CERT_FILENAME);
                    eeCert = readX509Cert(EE_CERT_FILENAME);

                    return true;
                } catch (Exception e) {
                    error = e;
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean result) {
                setProgressBarIndeterminateVisibility(false);

                if (result) {
                    certInfoText.setText("*CA*: " + getCertInfo(caCert));
                    certInfoText.append("\n\n");
                    certInfoText.append("*EE*: " + getCertInfo(eeCert));
                } else {
                    Toast.makeText(
                            MainActivity.this,
                            "Failed to load certificates, exiting: "
                                    + error.getMessage(), Toast.LENGTH_LONG)
                            .show();
                    finish();
                }
            }
        }.execute();
    }

    @Override
    public void onClick(View v) {
        try {
            switch (v.getId()) {
            case R.id.blacklist_ca_cert_button:
                String caPubKeyHash = getPublicKeyHash(caCert);
                Settings.Secure.putString(getContentResolver(),
                        PUBKEY_BLACKLIST_KEY, caPubKeyHash);
                break;
            case R.id.blacklist_ee_cert_button:
                String eeSerial = eeCert.getSerialNumber().toString(16);
                Settings.Secure.putString(getContentResolver(),
                        SERIAL_BLACKLIST_KEY, eeSerial);
                break;
            case R.id.clear_button:
                Settings.Secure.putString(getContentResolver(),
                        PUBKEY_BLACKLIST_KEY, "");
                Settings.Secure.putString(getContentResolver(),
                        SERIAL_BLACKLIST_KEY, "");
                break;
            case R.id.install_button:
                Intent intent = KeyChain.createInstallIntent();
                byte[] certBytes = caCertBytes;
                intent.putExtra(KeyChain.EXTRA_CERTIFICATE, certBytes);
                intent.putExtra(KeyChain.EXTRA_NAME, "myCA");
                startActivity(intent);
                break;
            case R.id.verify_button:
                TrustManagerFactory tmf = TrustManagerFactory
                        .getInstance("X509");
                Log.d(TAG, "TrustManagerFactory provider "
                        + tmf.getProvider().getName());
                tmf.init((KeyStore) null);
                TrustManager[] tms = tmf.getTrustManagers();
                Log.d(TAG, "num trust managers: " + tms.length);

                X509TrustManager xtm = (X509TrustManager) tms[0];
                Log.d(TAG, "checking chain with " + xtm);
                CertPathValidator cpv = CertPathValidator.getInstance("PKIX");
                Log.d(TAG, "PKIX validator provider: "
                        + cpv.getProvider().getName());
                Log.d(TAG, "PKIX validator class: " + cpv);

                X509Certificate[] chain = new X509Certificate[] { eeCert,
                        caCert };
                try {
                    xtm.checkClientTrusted(chain, "RSA");
                    messageText.setText("Cert chain is trusted.");
                } catch (CertificateException ce) {
                    Log.e(TAG, "Error validating certificate chain.", ce);
                    messageText.setText("Cert chain is NOT trusted: "
                            + ce.getMessage());
                }

                Class<?> blacklistClass = Class
                        .forName("com.android.org.bouncycastle.jce.provider.CertBlacklist");
                Object blacklist = blacklistClass.newInstance();
                Method isPubKeyBlacklisedMethod = blacklistClass.getMethod(
                        "isPublicKeyBlackListed", PublicKey.class);
                Method isSerialBlackListedMethod = blacklistClass.getMethod(
                        "isSerialNumberBlackListed", BigInteger.class);
                boolean isPubKeyBlackListed = (Boolean) isPubKeyBlacklisedMethod
                        .invoke(blacklist, caCert.getPublicKey());
                boolean isSerialBlackListed = (Boolean) isSerialBlackListedMethod
                        .invoke(blacklist, eeCert.getSerialNumber());

                messageText.append("\n");
                messageText.append("CA public key blacklisted: "
                        + isPubKeyBlackListed);
                messageText.append("\n");
                messageText.append("EE serial num blacklisted: "
                        + isSerialBlackListed);
                break;
            default:
                //
            }
        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage(), e);
            Toast.makeText(this, "Error:" + e.getMessage(), Toast.LENGTH_LONG)
                    .show();
        }
    }

    private static CharSequence getCertInfo(X509Certificate cert) {
        StringBuilder result = new StringBuilder();
        result.append("I: ");
        result.append(cert.getIssuerDN().getName());
        result.append("\n");
        result.append("S: ");
        result.append(cert.getSubjectDN().getName());
        result.append("\n");
        result.append("key hash: " + getPublicKeyHash(cert));
        result.append("\n");
        result.append("serial: " + cert.getSerialNumber().toString(16));

        return result.toString();
    }

    private static String getPublicKeyHash(X509Certificate cert) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA1");
            byte[] hash = md.digest(cert.getPublicKey().getEncoded());

            return toHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private X509Certificate readX509Cert(String filename)
            throws CertificateException, FileNotFoundException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        X509Certificate cert = (X509Certificate) cf
                .generateCertificate(new FileInputStream(new File(Environment
                        .getExternalStorageDirectory(), filename)));
        return cert;
    }

    private byte[] readFile(String filename) throws Exception {
        File f = new File(Environment.getExternalStorageDirectory(), filename);
        byte[] result = new byte[(int) f.length()];
        FileInputStream in = new FileInputStream(f);
        in.read(result);
        in.close();

        return result;
    }


    public static String toHex(byte[] bytes) {
        StringBuilder buff = new StringBuilder();
        for (byte b : bytes) {
            buff.append(String.format("%02x", b));
        }

        return buff.toString();
    }
}
