package onl.netfishers.netshot.ssh;

import onl.netfishers.netshot.Database;
import onl.netfishers.netshot.ssh.authentification.user.UserSsh;
import org.apache.mina.util.Base64;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.hibernate.HibernateException;
import org.hibernate.Session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;


public class SshPublicKeyAuthenticator implements PublickeyAuthenticator {

    //Converts a Java RSA PK to SSH2 Format.
    private static byte[] encode(RSAPublicKey key) {
        try {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] name = "ssh-rsa".getBytes("US-ASCII");
            write(name, buf);
            write(key.getPublicExponent().toByteArray(), buf);
            write(key.getModulus().toByteArray(), buf);
            return buf.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void write(byte[] str, OutputStream os) throws IOException {
        for (int shift = 24; shift >= 0; shift -= 8)
            os.write((str.length >>> shift) & 0xFF);
        os.write(str);
    }

    @Override
    public boolean authenticate(String user, PublicKey key, ServerSession session) {
        //String kTmp = "AAAAB3NzaC1yc2EAAAADAQABAAABAQCfxasnRAt8E+RusjA+koVjX2N3TrkmnPFhQ0O8uaSp7jZyj1vi1CNdwM7TRRvV0SlNmdTKNhK7JxFvtKBADRx+iagYj1+I4h36q7+ou5CssW6kQf+GjW73nstQm0oZlkSiQhip+bOhx/pkz/fYK7gKI2kjBVoD2g0SDuOrIM18XwkChQf9tSiHpCNUXQtLMVqZ4tQj1ERihO4d6VxcD5iYVtfcM1ULQwqXdNr3z7ptGUGsR0E/pZhT5x4rg8X9q6M4TxpH49+lJNCdvHPtAUU8VRVugwjnct2MY03dGXhyZ/XX+K/yCv4znJ8+mKB7pPyfrELA+bOA0KLlHD3YAAsZ";
        if (key instanceof RSAPublicKey) {
            Session s = Database.getSession();
            try {
                Object o = s.createQuery("FROM UserSsh u WHERE u.name=:name")
                        .setParameter("name", user.replaceAll("\\s", ""))
                        .uniqueResult();
                if (o != null) {
                    UserSsh u = (UserSsh) o;
                    if (u.getCertificat() != null) {
                        String s1 = new String(encode((RSAPublicKey) key));
                        String s2 = new String(Base64.decodeBase64(u.getCertificat().getBytes()));
                        return s1.equals(s2);
                    }
                }
            } catch (HibernateException e) {
                e.printStackTrace();
            } finally {
                s.close();
            }
        }
        return false;
    }
}