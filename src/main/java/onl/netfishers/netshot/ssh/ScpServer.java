package onl.netfishers.netshot.ssh;

import onl.netfishers.netshot.Database;
import onl.netfishers.netshot.Netshot;
import onl.netfishers.netshot.ssh.authentification.user.UserSsh;
import onl.netfishers.netshot.ssh.exception.ScpServerException;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.common.util.ValidateUtils;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.UserAuth;
import org.apache.sshd.server.auth.password.UserAuthPasswordFactory;
import org.apache.sshd.server.auth.pubkey.UserAuthPublicKeyFactory;
import org.apache.sshd.server.keyprovider.AbstractGeneratorHostKeyProvider;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.scp.ScpCommandFactory;
import org.apache.sshd.server.subsystem.sftp.SftpSubsystemFactory;
import org.hibernate.Session;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.*;


public class ScpServer {
    public static final SshPasswordAuthenticator passwordAuth = new SshPasswordAuthenticator();
    public static final SshPublicKeyAuthenticator publicKeyAuth = new SshPublicKeyAuthenticator();

    private static final SshServer sshd = SshServer.setUpDefaultServer();
    private static Status status = Status.STOPPED;

    private static String DEFAULT_FOLDER = Netshot.getConfig("netshot.watch.folderListen");
    private static String DEFAULT_PORT = Netshot.getConfig("netshot.ssh.portListen");

    private static void start() throws ScpServerException {
        try {
            if (status == Status.STOPPED) {
                status = Status.STARTING;

                sshd.setPort(Integer.parseInt(DEFAULT_PORT));
                initCertServer();

                sshd.setCommandFactory(new ScpCommandFactory());

                initUserHomeFolder();

                sshd.setPasswordAuthenticator(passwordAuth);
                sshd.setPublickeyAuthenticator(publicKeyAuth);

                sshd.setSubsystemFactories(Arrays.<NamedFactory<Command>>asList(new SftpSubsystemFactory()));

                sshd.start();

                System.err.println("SCP Server has been started successfully.");
                status = Status.STARTED;
            } else {
                throw new ScpServerException("The SCP Server is not STOPPED!");
            }

        } catch (Exception ex) {
            throw new ScpServerException(ex.toString());
        }
    }

    public static void stop() throws ScpServerException {
        try {
            if (status == Status.STARTED) {
                status = Status.STOPPING;
                sshd.stop();
                System.err.println("SCP Server has been stopped successfully.");

                status = Status.STOPPED;
            } else {
                throw new ScpServerException("The SCP Server is not STARTED!");
            }
        } catch (Exception ex) {
            throw new ScpServerException(ex.toString());
        }
    }

    private static void initUserHomeFolder() {
        Session session = Database.getSession();
        List list = session.createCriteria(UserSsh.class).list();
        Set<Object> s = new HashSet<Object>(list);
        VirtualFileSystemFactory fsFactory = new VirtualFileSystemFactory();
        for (Object o : s) {
            UserSsh userSsh = (UserSsh) o;
            fsFactory.setUserHomeDir(userSsh.getName(), Paths.get(DEFAULT_FOLDER));
        }
        sshd.setFileSystemFactory(fsFactory);
    }

    private static void initCertServer() throws IOException {

        String hostKeyType = AbstractGeneratorHostKeyProvider.DEFAULT_ALGORITHM;

        AbstractGeneratorHostKeyProvider hostKeyProvider;
        Path hostKeyFile;
        if (SecurityUtils.isBouncyCastleRegistered()) {
            hostKeyFile = new File("key.pem").toPath();
            hostKeyProvider = SecurityUtils.createGeneratorHostKeyProvider(hostKeyFile);
        } else {
            hostKeyFile = new File("key.ser").toPath();
            hostKeyProvider = new SimpleGeneratorHostKeyProvider(hostKeyFile);
        }
        hostKeyProvider.setAlgorithm(hostKeyType);

        List<KeyPair> keys = ValidateUtils.checkNotNullAndNotEmpty(hostKeyProvider.loadKeys(),
                "Failed to load keys from %s", hostKeyFile);
        KeyPair kp = keys.get(0);
        PublicKey pubKey = kp.getPublic();
        String keyAlgorithm = pubKey.getAlgorithm();
        // force re-generation of host key if not same algorithm
        if (!Objects.equals(keyAlgorithm, hostKeyProvider.getAlgorithm())) {
            Files.deleteIfExists(hostKeyFile);
            hostKeyProvider.clearLoadedKeys();
        }
        sshd.setKeyPairProvider(hostKeyProvider);
    }

    public static void initServer() {
        try {
            ScpServer.start();
            Thread.sleep(Long.MAX_VALUE);
            initServer();
        } catch (Exception ex) {
            System.err.println(ex.toString());
        }
    }

    private enum Status {
        STOPPED, STARTING, STARTED, STOPPING
    }
}
