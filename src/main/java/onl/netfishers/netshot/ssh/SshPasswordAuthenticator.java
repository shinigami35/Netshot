package onl.netfishers.netshot.ssh;

import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.session.ServerSession;

import java.util.HashMap;
import java.util.Map;

public class SshPasswordAuthenticator implements PasswordAuthenticator {

    private Map<String, String> hash = new HashMap<String, String>();

    private void load() {
        hash.put("test", "test");
        hash.put("agm", "agm");
        hash.put("nomios", "nomios");
    }


    @Override
    public boolean authenticate(String username, String password, ServerSession session) {
        load();

        for (Map.Entry<String, String> entry : hash.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (username.equals(key) && password.equals(value))
                return true;
        }
        return false;
    }
}