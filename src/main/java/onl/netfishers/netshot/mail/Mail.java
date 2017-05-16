package onl.netfishers.netshot.mail;

import onl.netfishers.netshot.Netshot;
import onl.netfishers.netshot.device.Device;
import onl.netfishers.netshot.work.Task;
import onl.netfishers.netshot.work.tasks.TakeSnapshotTask;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

public class Mail {

    private static Mail INSTANCE = null;

    private String sender = null;
    private String receiver = null;
    private String username = null;
    private String password = null;
    private String smtp = null;
    private String port = null;
    private Boolean tls = false;

    private Mail() {
        this.sender = Netshot.getConfig("netshot.mail.from");
        this.receiver = Netshot.getConfig("netshot.mail.contact");
        this.username = Netshot.getConfig("netshot.mail.username");
        this.password = Netshot.getConfig("netshot.mail.password");
        this.smtp = Netshot.getConfig("netshot.mail.smtp");
        this.port = Netshot.getConfig("netshot.mail.port");
        this.tls = (Netshot.getConfig("netshot.mail.tls").toLowerCase().equals("true"));
    }

    public static Mail getInstance() {
        if (INSTANCE == null)
            INSTANCE = new Mail();
        return INSTANCE;
    }

    public void sendEmail(TakeSnapshotTask t, String date) {

        Task.Status type = t.getStatus();
        Device device = t.getDevice();
        String log = t.getLog();
        String[] tmpEmails = device.getEmails().replaceAll("\\s+", "").split(";");

        String from = this.sender;

        Properties props = new Properties();
        props.put("mail.smtp.host", this.smtp.replaceAll("\\s+", ""));
        if (this.tls) {
            props.put("mail.smtp.socketFactory.port", String.valueOf(this.port));
            props.put("mail.smtp.socketFactory.class",
                    "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.auth", (this.tls ? "true" : "false"));
        }
        props.put("mail.smtp.port", String.valueOf(this.port));

        Session session = Session.getInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });
        try {
            for (String s : tmpEmails) {

                MimeMessage message = new MimeMessage(session);
                message.setHeader("Content-Type", "text/plain; charset=UTF-8");
                message.setFrom(new InternetAddress(from));
                message.addRecipient(Message.RecipientType.TO, new InternetAddress(s));

                if (type == Task.Status.SUCCESS && device.getOnSuccess()) {
                    message.setSubject("[" + device.getName() + "] Le snapshot du " + date + " a été fait sans problème");
                    try {
                        message.setContent(new String(generateBodyMail(type, date, device.getName(), log).getBytes(), "UTF-8"), "text/html");
                    } catch (UnsupportedEncodingException e) {
                        message.setContent(generateBodyMail(type, date, device.getName(), log), "text/html");
                    }
                    Transport.send(message);
                } else if (type == Task.Status.FAILURE && device.getOnError()) {
                    message.setSubject("[" + device.getName() + "] Le snapshot du " + date + " n'a pas été effectué correctement");
                    try {
                        message.setContent(new String(generateBodyMail(type, date, device.getName(), log).getBytes(), "UTF-8"), "text/html");
                    } catch (UnsupportedEncodingException e) {
                        message.setContent(generateBodyMail(type, date, device.getName(), log), "text/html");
                    }
                    Transport.send(message);
                }
            }
        } catch (MessagingException mex) {
            mex.printStackTrace();
        }
    }

    private String generateBodyMail(Task.Status s, String date, String device, String log) {
        StringBuilder tmp = new StringBuilder();
        if (s == Task.Status.SUCCESS) {
            tmp.append("<h2>Snapshot <font color=\"green\">SUCCESS</font></h2>" + "<p>Le snapshot du ")
                    .append(date)
                    .append(" a été fait sans problème pour : ")
                    .append(device).append("</p>");
            String[] t = log.split("\n");
            for (String sTmp : t)
                tmp.append("<p style=\"font-family: monospace;max-height: 200px;overflow-y: scroll;\">")
                        .append(sTmp)
                        .append("</p>");
        } else if (s == Task.Status.FAILURE) {
            tmp.append("<h2>Snapshot <font color=\"red\">FAILURE</font></h2>" + "<p>Le snapshot du ")
                    .append(date)
                    .append(" n'a pas été effectué correctement pour : ")
                    .append(device)
                    .append("</p>");
            String[] t = log.split("\n");
            for (String sTmp : t)
                tmp.append("<p style=\"font-family: monospace;max-height: 200px;overflow-y: scroll;\">").append(sTmp).append("</p>");
        }
        try {
            return new String(tmp.toString().getBytes(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return tmp.toString();
        }
    }
}
