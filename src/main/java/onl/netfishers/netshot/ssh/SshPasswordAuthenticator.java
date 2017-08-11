package onl.netfishers.netshot.ssh;

import onl.netfishers.netshot.Database;
import onl.netfishers.netshot.ssh.authentification.user.UserSsh;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SshPasswordAuthenticator implements PasswordAuthenticator {

    /**
     * The logger.
     */
    private static Logger logger = LoggerFactory.getLogger(SshPasswordAuthenticator.class);

    @Override
    public boolean authenticate(String username, String password, ServerSession session) {
        Session s = Database.getSession();
        logger.info("Get connection by Key | User : " + username);
        try {
            Object o = s.createQuery("FROM UserSsh u WHERE u.name=:name")
                    .setParameter("name", username.replaceAll("\\s", ""))
                    .uniqueResult();
            if (o != null) {
                UserSsh u = (UserSsh) o;
                if (u.getPassword() != null && !u.getPassword().equals("")) {
                    return u.getPassword().equals(password);
                }
            }
        } catch (HibernateException e) {
            e.printStackTrace();
        } finally {
            s.close();
        }
        return false;
    }
}