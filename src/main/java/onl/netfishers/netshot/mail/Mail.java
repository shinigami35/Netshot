package onl.netfishers.netshot.mail;

import onl.netfishers.netshot.work.Task;

import java.util.*;
import javax.mail.*;
import javax.mail.internet.*;

public class Mail {

    private String sender = "";
    private String receiver = "";
    //private String smtp = "";
    private String password = "russe35580";
    private String username = "russe35580@gmail.com";

    public Mail(String sender, String receiver) {
        this.sender = sender;
        this.receiver = receiver;
        //this.smtp = smtp;
    }

    public void sendEmail(Task.Status type, String date, String device, String log) {

        String to = this.receiver;
        String from = this.sender;

        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.socketFactory.port", "465");
        props.put("mail.smtp.socketFactory.class",
                "javax.net.ssl.SSLSocketFactory");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.port", "465");

        Session session = Session.getInstance(props,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username, password);
                    }
                });

        try {
            // Create a default MimeMessage object.
            MimeMessage message = new MimeMessage(session);

            // Set From: header field of the header.
            message.setFrom(new InternetAddress(from));

            // Set To: header field of the header.
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            if (type == Task.Status.SUCCESS) {
                message.setSubject("Le snapshot du " + date + " a été fait sans problème : " + device);
                message.setContent(generateBodyMail(type, date, device, log), "text/html");
                Transport.send(message);
            } else if (type == Task.Status.FAILURE) {
                message.setSubject("Le snapshot du " + date + " n'a pas été effectué correctement pour : " + device);
                message.setContent(generateBodyMail(type, date, device, log), "text/html");
                Transport.send(message);
            }

        } catch (MessagingException mex) {
            mex.printStackTrace();
        }
    }

    private String generateBodyMail(Task.Status s, String date, String device, String log) {
        if (s == Task.Status.SUCCESS)
            return "<h2>Snapshot <font color=\"green\">SUCCESS</font></h2>" +
                    "<p>Le snapshot du " + date + " a été fait sans problème pour : " + device + "</p>";
        else if (s == Task.Status.FAILURE)
            return "<h2>Snapshot <font color=\"red\">FAILURE</font></h2>" +
                    "<p>Le snapshot du " + date + " n'a pas été effectué correctement pour : " + device + "</p>" +
                    "<p>" + log + "</p>";
        return "";
    }

}
