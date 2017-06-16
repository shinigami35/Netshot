package onl.netfishers.netshot.ssh;

import onl.netfishers.netshot.Netshot;
import onl.netfishers.netshot.ssh.exception.ScpServerException;
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.scp.ScpCommandFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;


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
                sshd.setKeyPairProvider(new SimpleGeneratorHostKeyProvider());

                sshd.setPublickeyAuthenticator(publicKeyAuth);
                //sshd.setPasswordAuthenticator(passwordAuth);
                sshd.setCommandFactory(new ScpCommandFactory());

                initUserHomeFolder();

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

    private enum Status {
        STOPPED, STARTING, STARTED, STOPPING
    }

    private static void initUserHomeFolder(){
        VirtualFileSystemFactory fsFactory = new VirtualFileSystemFactory();
        fsFactory.setUserHomeDir("netshot", Paths.get(DEFAULT_FOLDER));
        fsFactory.setUserHomeDir("netshotssh", Paths.get(DEFAULT_FOLDER));
        fsFactory.setUserHomeDir("netshotpwd", Paths.get(DEFAULT_FOLDER));
        sshd.setFileSystemFactory(fsFactory);
    }

    public static void initServer(){
        try{
            ScpServer.start();
            Thread.sleep(Long.MAX_VALUE);
            initServer();
        } catch (Exception ex){
            System.err.println(ex.toString());
        }
    }
}
