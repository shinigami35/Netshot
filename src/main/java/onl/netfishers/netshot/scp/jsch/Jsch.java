package onl.netfishers.netshot.scp.jsch;

import com.jcraft.jsch.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created by agm on 26/05/2017.
 */
public class Jsch {

    private static final Logger LOGGER =
            Logger.getLogger(Jsch.class.getName());
    private JSch jschSSHChannel;
    private String strUserName;
    private String strConnectionIP;
    private int intConnectionPort;
    private String strPassword;
    private Session sesConnection;
    private int intTimeOut;

    public Jsch(String userName, String password, String connectionIP, String knownHostsFileName) {
        init(userName, password, connectionIP, knownHostsFileName);
        intConnectionPort = 22;
        intTimeOut = 60000;
    }

    public Jsch(String userName, String password, String connectionIP,
                String knownHostsFileName, int connectionPort) {
        init(userName, password, connectionIP,
                knownHostsFileName);
        intConnectionPort = connectionPort;
        intTimeOut = 60000;
    }

    public Jsch(String userName, String password, String connectionIP,
                String knownHostsFileName, int connectionPort, int timeOutMilliseconds) {
        init(userName, password, connectionIP,
                knownHostsFileName);
        intConnectionPort = connectionPort;
        intTimeOut = timeOutMilliseconds;
    }

    private void init(String userName, String password, String connectionIP, String knownHostsFileName) {
        jschSSHChannel = new JSch();

        try {
            jschSSHChannel.setKnownHosts(knownHostsFileName);
        } catch (JSchException jschX) {
            logError(jschX.getMessage());
        }

        strUserName = userName;
        strPassword = password;
        strConnectionIP = connectionIP;
    }

    public String connect() {
        String errorMessage = null;

        try {
            sesConnection = jschSSHChannel.getSession(strUserName, strConnectionIP, intConnectionPort);
            sesConnection.setPassword(strPassword);
             sesConnection.setConfig("StrictHostKeyChecking", "no");
            sesConnection.connect(intTimeOut);
        } catch (JSchException jschX) {
            errorMessage = jschX.getMessage();
        }

        return errorMessage;
    }

    private String logError(String errorMessage) {
        if (errorMessage != null) {
            LOGGER.log(Level.SEVERE, "{0}:{1} - {2}",
                    new Object[]{strConnectionIP, intConnectionPort, errorMessage});
        }

        return errorMessage;
    }

    private String logWarning(String warnMessage) {
        if (warnMessage != null) {
            LOGGER.log(Level.WARNING, "{0}:{1} - {2}",
                    new Object[]{strConnectionIP, intConnectionPort, warnMessage});
        }

        return warnMessage;
    }

    public String sendCommand(String command) {
        StringBuilder outputBuffer = new StringBuilder();

        try {
            Channel channel = sesConnection.openChannel("exec");
            ((ChannelExec) channel).setCommand(command);
            InputStream commandOutput = channel.getInputStream();
            channel.connect();
            int readByte = commandOutput.read();

            while (readByte != 0xffffffff) {
                outputBuffer.append((char) readByte);
                readByte = commandOutput.read();
            }
            System.out.println(outputBuffer);

            channel.disconnect();
        } catch (IOException | JSchException ioX) {
            logWarning(ioX.getMessage());
            return null;
        }

        return outputBuffer.toString();
    }

    public void close() {
        sesConnection.disconnect();
    }

}
