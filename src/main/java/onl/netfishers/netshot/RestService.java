/**
 * Copyright 2013-2016 Sylvain Cadilhac (NetFishers)
 * <p>
 * This file is part of Netshot.
 * <p>
 * Netshot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * Netshot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with Netshot.  If not, see <http://www.gnu.org/licenses/>.
 */
package onl.netfishers.netshot;

import difflib.Delta;
import difflib.DiffUtils;
import difflib.Patch;
import onl.netfishers.netshot.aaa.Radius;
import onl.netfishers.netshot.aaa.User;
import onl.netfishers.netshot.compliance.*;
import onl.netfishers.netshot.compliance.SoftwareRule.ConformanceLevel;
import onl.netfishers.netshot.compliance.rules.JavaScriptRule;
import onl.netfishers.netshot.compliance.rules.TextRule;
import onl.netfishers.netshot.device.*;
import onl.netfishers.netshot.device.Device.MissingDeviceDriverException;
import onl.netfishers.netshot.device.Device.Status;
import onl.netfishers.netshot.device.DeviceDriver.AttributeDefinition;
import onl.netfishers.netshot.device.Finder.Expression.FinderParseException;
import onl.netfishers.netshot.device.attribute.ConfigAttribute;
import onl.netfishers.netshot.device.attribute.ConfigLongTextAttribute;
import onl.netfishers.netshot.device.credentials.DeviceCliAccount;
import onl.netfishers.netshot.device.credentials.DeviceCredentialSet;
import onl.netfishers.netshot.device.credentials.DeviceSnmpCommunity;
import onl.netfishers.netshot.device.credentials.DeviceSshKeyAccount;
import onl.netfishers.netshot.http.MappingHttp;
import onl.netfishers.netshot.scp.device.Company;
import onl.netfishers.netshot.scp.device.ScpStepFolder;
import onl.netfishers.netshot.scp.device.Types;
import onl.netfishers.netshot.scp.device.VirtualDevice;
import onl.netfishers.netshot.ssh.authentification.user.UserSsh;
import onl.netfishers.netshot.work.Task;
import onl.netfishers.netshot.work.Task.ScheduleType;
import onl.netfishers.netshot.work.tasks.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.glassfish.grizzly.http.server.CLStaticHttpHandler;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.servlet.ServletRegistration;
import org.glassfish.grizzly.servlet.WebappContext;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpContainer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.servlet.ServletProperties;
import org.hibernate.*;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Property;
import org.hibernate.criterion.Restrictions;
import org.hibernate.transform.Transformers;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MarkerFactory;

import javax.annotation.security.DenyAll;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import javax.ws.rs.*;
import javax.ws.rs.core.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static onl.netfishers.netshot.scp.job.JobTools.*;

/**
 * The RestService class exposes the Netshot methods as a REST service.
 */
@Path("/")
@DenyAll
public class RestService extends Thread {


    /**
     * The HQL select query for "light" devices, to be prepended to the actual query.
     */
    private static final String DEVICELIST_BASEQUERY = "select d.id as id, d.name as name, d.family as family, d.mgmtAddress as mgmtAddress, d.status as status ";

    /**
     * The logger.
     */
    private static Logger logger = LoggerFactory
            .getLogger(RestService.class);

    /**
     * The static instance service.
     */
    private static RestService nsRestService;
    private String httpStaticPath;
    private String httpApiPath;
    private String httpBaseUrl;
    private String httpSslKeystoreFile;
    private String httpSslKeystorePass;
    private int httpBasePort;

    /**
     * Instantiates a new Netshot REST service.
     */
    public RestService() {
        this.setName("REST Service");
        httpStaticPath = Netshot.getConfig("netshot.http.staticpath", "/");
        httpApiPath = Netshot.getConfig("netshot.http.apipath", "/api");
        httpBaseUrl = Netshot.getConfig("netshot.http.baseurl", "http://localhost:8443");
        httpSslKeystoreFile = Netshot.getConfig("netshot.http.ssl.keystore.file", "netshot.jks");
        httpSslKeystorePass = Netshot.getConfig("netshot.http.ssl.keystore.pass", "netshotpass");
        httpBasePort = 8443;
        try {
            httpBasePort = Integer.parseInt(Netshot.getConfig("netshot.http.baseport",
                    Integer.toString(httpBasePort)));
        } catch (Exception e) {
            logger.warn("Unable to understand the HTTP base port configuration, using {}.",
                    httpBasePort);
        }
    }

    /**
     * Initializes the service.
     */
    public static void init() {
        nsRestService = new RestService();
        nsRestService.setUncaughtExceptionHandler(Netshot.exceptionHandler);
        nsRestService.start();
    }


    private static void deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deleteFolder(f);
                } else {
                    f.delete();
                }
            }
        }
        folder.delete();
    }

    /* (non-Javadoc)
     * @see java.lang.Thread#run()
	 */
    public void run() {
        logger.info("Starting the Web/REST service thread.");
        try {

            SSLContextConfigurator sslContext = new SSLContextConfigurator();
            sslContext.setKeyStoreFile(httpSslKeystoreFile);
            sslContext.setKeyStorePass(httpSslKeystorePass);

            if (!sslContext.validateConfiguration(true)) {
                throw new RuntimeException(
                        "Invalid SSL settings for the embedded HTTPS server.");
            }
            SSLEngineConfigurator sslConfig = new SSLEngineConfigurator(sslContext)
                    .setClientMode(false).setNeedClientAuth(false).setWantClientAuth(false);
            URI url = UriBuilder.fromUri(httpBaseUrl).port(httpBasePort).build();
            HttpServer server = GrizzlyHttpServerFactory.createHttpServer(
                    url, (GrizzlyHttpContainer) null, true, sslConfig, false);

            WebappContext context = new WebappContext("GrizzlyContext", httpApiPath);
            ServletRegistration registration = context.addServlet("Jersey", ServletContainer.class);
            registration.setInitParameter(ServletProperties.JAXRS_APPLICATION_CLASS,
                    MappingHttp.NetshotWebApplication.class.getName());
            registration.addMapping(httpApiPath);
            context.deploy(server);
            HttpHandler staticHandler = new CLStaticHttpHandler(Netshot.class.getClassLoader(), "/www/");
            server.getServerConfiguration().addHttpHandler(staticHandler, httpStaticPath);


            server.start();

            synchronized (this) {
                while (true) {
                    this.wait();
                }
            }
        } catch (Exception e) {
            logger.error(MarkerFactory.getMarker("FATAL"),
                    "Fatal error with the REST service.", e);
            throw new RuntimeException(
                    "Error with the REST service, see logs for more details.");
        }
    }

    /**
     * Gets the domains.
     *
     * @return the domains
     * @throws WebApplicationException the web application exception
     */
    @SuppressWarnings("unchecked")
    @GET
    @Path("domains")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<MappingHttp.RsDomain> getDomains() throws WebApplicationException {
        logger.debug("REST request, domains.");
        Session session = Database.getSession();
        List<Domain> domains;
        try {
            domains = session.createCriteria(Domain.class).list();
            List<MappingHttp.RsDomain> rsDomains = new ArrayList<MappingHttp.RsDomain>();
            for (Domain domain : domains) {
                rsDomains.add(new MappingHttp.RsDomain(domain));
            }
            return rsDomains;
        } catch (HibernateException e) {
            logger.error("Unable to fetch the domains.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the domains",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Adds the domain.
     *
     * @param request   the request
     * @param newDomain the new domain
     * @return the rs domain
     * @throws WebApplicationException the web application exception
     */
    @POST
    @Path("domains")
    @RolesAllowed("admin")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public MappingHttp.RsDomain addDomain(MappingHttp.RsDomain newDomain) throws WebApplicationException {
        logger.debug("REST request, add a domain");
        String name = newDomain.getName().trim();
        if (name.isEmpty()) {
            logger.warn("User posted an empty domain name.");
            throw new MappingHttp.NetshotBadRequestException("Invalid domain name.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DOMAIN_NAME);
        }
        String description = newDomain.getDescription().trim();
        try {
            Network4Address v4Address = new Network4Address(newDomain.getIpAddress());
            Network6Address v6Address = new Network6Address("::");
            if (!v4Address.isNormalUnicast()) {
                logger.warn("User posted an invalid IP address.");
                throw new MappingHttp.NetshotBadRequestException("Invalid IP address",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_IP_ADDRESS);
            }
            Domain domain = new Domain(name, description, v4Address, v6Address);
            Session session = Database.getSession();
            try {
                session.beginTransaction();
                session.save(domain);
                session.getTransaction().commit();
            } catch (HibernateException e) {
                session.getTransaction().rollback();
                logger.error("Error while adding a domain.", e);
                Throwable t = e.getCause();
                if (t != null && t.getMessage().contains("Duplicate entry")) {
                    throw new MappingHttp.NetshotBadRequestException(
                            "A domain with this name already exists.",
                            MappingHttp.NetshotBadRequestException.NETSHOT_DUPLICATE_DOMAIN);
                }
                throw new MappingHttp.NetshotBadRequestException(
                        "Unable to add the domain to the database",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
            } finally {
                session.close();
            }
            return new MappingHttp.RsDomain(domain);
        } catch (UnknownHostException e) {
            logger.warn("User posted an invalid IP address.");
            throw new MappingHttp.NetshotBadRequestException("Malformed IP address",
                    MappingHttp.NetshotBadRequestException.NETSHOT_MALFORMED_IP_ADDRESS);
        }
    }

    /**
     * Sets the domain.
     *
     * @param request  the request
     * @param id       the id
     * @param rsDomain the rs domain
     * @return the rs domain
     * @throws WebApplicationException the web application exception
     */
    @PUT
    @Path("domains/{id}")
    @RolesAllowed("admin")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public MappingHttp.RsDomain setDomain(@PathParam("id") Long id, MappingHttp.RsDomain rsDomain)
            throws WebApplicationException {
        logger.debug("REST request, edit domain {}.", id);
        String name = rsDomain.getName().trim();
        if (name.isEmpty()) {
            logger.warn("User posted an invalid domain name.");
            throw new MappingHttp.NetshotBadRequestException("Invalid domain name.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DOMAIN_NAME);
        }
        String description = rsDomain.getDescription().trim();
        Network4Address v4Address;
        try {
            v4Address = new Network4Address(rsDomain.getIpAddress());
            if (!v4Address.isNormalUnicast()) {
                logger.warn("User posted an invalid IP address");
                throw new MappingHttp.NetshotBadRequestException("Invalid IP address",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_IP_ADDRESS);
            }
        } catch (UnknownHostException e) {
            logger.warn("Invalid IP address.", e);
            throw new MappingHttp.NetshotBadRequestException("Malformed IP address",
                    MappingHttp.NetshotBadRequestException.NETSHOT_MALFORMED_IP_ADDRESS);
        }
        Session session = Database.getSession();
        Domain domain;
        try {
            session.beginTransaction();
            domain = (Domain) session.load(Domain.class, id);
            domain.setName(name);
            domain.setDescription(description);
            domain.setServer4Address(v4Address);
            session.update(domain);
            session.getTransaction().commit();
        } catch (ObjectNotFoundException e) {
            session.getTransaction().rollback();
            logger.error("The domain doesn't exist.", e);
            throw new MappingHttp.NetshotBadRequestException("The domain doesn't exist.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DOMAIN);
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Error while editing the domain.", e);
            Throwable t = e.getCause();
            if (t != null && t.getMessage().contains("Duplicate entry")) {
                throw new MappingHttp.NetshotBadRequestException(
                        "A domain with this name already exists.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DUPLICATE_DOMAIN);
            }
            throw new MappingHttp.NetshotBadRequestException(
                    "Unable to save the domain... is the name already in use?",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
        return new MappingHttp.RsDomain(domain);
    }

    /**
     * Delete a domain.
     *
     * @param id the id
     * @throws WebApplicationException the web application exception
     */
    @DELETE
    @Path("domains/{id}")
    @RolesAllowed("admin")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public void deleteDomain(@PathParam("id") Long id)
            throws WebApplicationException {
        logger.debug("REST request, delete domain {}.", id);
        Session session = Database.getSession();
        try {
            session.beginTransaction();
            Domain domain = (Domain) session.load(Domain.class, id);
            session.delete(domain);
            session.getTransaction().commit();
        } catch (ObjectNotFoundException e) {
            session.getTransaction().rollback();
            logger.error("The domain doesn't exist.");
            throw new MappingHttp.NetshotBadRequestException("The domain doesn't exist.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DOMAIN);
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            Throwable t = e.getCause();
            if (t != null && t.getMessage().contains("foreign key constraint fails")) {
                throw new MappingHttp.NetshotBadRequestException(
                        "Unable to delete the domain, there must be devices or tasks using it.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_USED_DOMAIN);
            }
            throw new MappingHttp.NetshotBadRequestException("Unable to delete the domain",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Gets the device interfaces.
     *
     * @param id the id
     * @return the device interfaces
     * @throws WebApplicationException the web application exception
     */
    @SuppressWarnings("unchecked")
    @GET
    @Path("devices/{id}/interfaces")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<NetworkInterface> getDeviceInterfaces(@PathParam("id") Long id)
            throws WebApplicationException {
        logger.debug("REST request, get device {} interfaces.", id);
        Session session = Database.getSession();
        try {
            List<NetworkInterface> deviceInterfaces;
            deviceInterfaces = session
                    .createQuery(
                            "from NetworkInterface AS networkInterface "
                                    + "left join fetch networkInterface.ip4Addresses "
                                    + "left join fetch networkInterface.ip6Addresses "
                                    + "where device = :device").setLong("device", id)
                    .setResultTransformer(Criteria.DISTINCT_ROOT_ENTITY).list();
            return deviceInterfaces;
        } catch (HibernateException e) {
            logger.error("Unable to fetch the interfaces.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the interfaces",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Gets the device modules.
     *
     * @param id the id
     * @return the device modules
     * @throws WebApplicationException the web application exception
     */
    @SuppressWarnings("unchecked")
    @GET
    @Path("devices/{id}/modules")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<Module> getDeviceModules(@PathParam("id") Long id)
            throws WebApplicationException {
        logger.debug("REST request, get device {} modules.", id);
        Session session = Database.getSession();
        try {
            List<Module> deviceModules = session
                    .createQuery("from Module m where device = :device")
                    .setLong("device", id).list();
            return deviceModules;
        } catch (HibernateException e) {
            logger.error("Unable to fetch the modules.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the modules",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Gets the device last 20 tasks.
     *
     * @param id the id
     * @return the device tasks
     * @throws WebApplicationException the web application exception
     */
    @SuppressWarnings("unchecked")
    @GET
    @Path("devices/{id}/tasks")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<Task> getDeviceTasks(@PathParam("id") Long id)
            throws WebApplicationException {
        logger.debug("REST request, get device {} tasks.", id);
        Session session = Database.getSession();
        try {
            final int max = 20;
            final Class<?>[] taskTypes = new Class<?>[]{
                    CheckComplianceTask.class,
                    DiscoverDeviceTypeTask.class,
                    TakeSnapshotTask.class,
                    RunDeviceScriptTask.class
            };
            final Criterion[] restrictions = new Criterion[]{
                    Restrictions.eq("t.device.id", id),
                    Restrictions.eq("t.deviceId", id),
                    Restrictions.eq("t.device.id", id),
                    Restrictions.eq("t.device.id", id)
            };
            List<Task> tasks = new ArrayList<Task>();
            for (int i = 0; i < taskTypes.length; i++) {
                List<Task> typeTasks = session
                        .createCriteria(taskTypes[i], "t")
                        .add(restrictions[i])
                        .list();
                tasks.addAll(typeTasks);
            }
            Collections.sort(tasks, new Comparator<Task>() {
                private int getPriority(Task.Status status) {
                    switch (status) {
                        case RUNNING:
                            return 1;
                        case WAITING:
                            return 2;
                        case SCHEDULED:
                            return 3;
                        case NEW:
                            return 4;
                        default:
                            return 10;
                    }
                }

                private Date getSignificantDate(Task t) {
                    if (t.getExecutionDate() == null) {
                        return t.getChangeDate();
                    } else {
                        return t.getExecutionDate();
                    }
                }

                @Override
                public int compare(Task o1, Task o2) {
                    int statusDiff = Integer.compare(
                            this.getPriority(o1.getStatus()), this.getPriority(o2.getStatus()));
                    if (statusDiff == 0) {
                        Date d1 = this.getSignificantDate(o1);
                        Date d2 = this.getSignificantDate(o2);
                        if (d1 == null) {
                            if (d2 == null) {
                                return 0;
                            } else {
                                return -1;
                            }
                        } else {
                            if (d2 == null) {
                                return 1;
                            } else {
                                return d2.compareTo(d1);
                            }
                        }
                    }
                    return statusDiff;
                }
            });
            return tasks.subList(0, (max > tasks.size() ? tasks.size() : max));
        } catch (Exception e) {
            logger.error("Unable to fetch the tasks.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the tasks",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Gets the device configs.
     *
     * @param id the id
     * @return the device configs
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("devices/{id}/configs")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<Config> getDeviceConfigs(@PathParam("id") Long id)
            throws WebApplicationException {
        logger.debug("REST request, get device {} configs.", id);
        Session session = Database.getSession();
        try {
            session.enableFilter("lightAttributesOnly");
            @SuppressWarnings("unchecked")
            List<Config> deviceConfigs = session
                    .createQuery("from Config c left join fetch c.attributes ca where c.device = :device")
                    .setLong("device", id).list();
            return deviceConfigs;
        } catch (HibernateException e) {
            logger.error("Unable to fetch the configs.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the configs",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Gets the device config plain.
     *
     * @param id   the id
     * @param item the item
     * @return the device config plain
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("configs/{id}/{item}")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_OCTET_STREAM})
    public Response getDeviceConfigPlain(@PathParam("id") Long id,
                                         @PathParam("item") String item) throws WebApplicationException {
        logger.debug("REST request, get device {id} config {}.", id, item);
        Session session = Database.getSession();
        try {
            Config config = (Config) session.get(Config.class, id);
            if (config == null) {
                logger.warn("Unable to find the config object.");
                throw new WebApplicationException(
                        "Unable to find the configuration set",
                        javax.ws.rs.core.Response.Status.NOT_FOUND);
            }
            String text = null;
            for (ConfigAttribute attribute : config.getAttributes()) {
                if (attribute.getName().equals(item) && attribute instanceof ConfigLongTextAttribute) {
                    text = ((ConfigLongTextAttribute) attribute).getLongText().getText();
                    break;
                }
            }
            if (text == null) {
                throw new WebApplicationException("Configuration item not available",
                        javax.ws.rs.core.Response.Status.BAD_REQUEST);
            }
            String fileName = "config.cfg";
            try {
                SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd_HH-mm");
                fileName = String.format("%s_%s.cfg", config.getDevice().getName(), formatter.format(config.getChangeDate()));
            } catch (Exception e) {
            }
            return Response.ok(text)
                    .header("Content-Disposition", "attachment; filename=" + fileName)
                    .build();
        } catch (HibernateException e) {
            throw new WebApplicationException("Unable to get the configuration",
                    javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Gets the device config diff.
     *
     * @param id1 the id1
     * @param id2 the id2
     * @return the device config diff
     */
    @GET
    @Path("configs/{id1}/vs/{id2}")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON})
    public MappingHttp.RsConfigDiff getDeviceConfigDiff(@PathParam("id1") Long id1,
                                                        @PathParam("id2") Long id2) {
        logger.debug("REST request, get device config diff, id {} and {}.", id1,
                id2);
        MappingHttp.RsConfigDiff configDiffs;
        Session session = Database.getSession();
        Config config1 = null;
        Config config2 = null;
        try {
            config2 = (Config) session.get(Config.class, id2);
            if (config2 != null && id1 == 0) {
                config1 = (Config) session
                        .createQuery("from Config c where c.device = :device and c.changeDate < :date2 order by c.changeDate desc")
                        .setEntity("device", config2.getDevice())
                        .setTimestamp("date2", config2.getChangeDate())
                        .setMaxResults(1)
                        .uniqueResult();
                if (config1 == null) {
                    config1 = new Config(config2.getDevice());
                }
            } else {
                config1 = (Config) session.get(Config.class, id1);
            }
            if (config1 == null || config2 == null) {
                logger.error("Non existing config, {} or {}.", id1, id2);
                throw new MappingHttp.NetshotBadRequestException("Unable to fetch the configs",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
            }
            DeviceDriver driver1;
            DeviceDriver driver2;
            try {
                driver1 = config1.getDevice().getDeviceDriver();
                driver2 = config2.getDevice().getDeviceDriver();
            } catch (MissingDeviceDriverException e) {
                logger.error("Missing driver.");
                throw new MappingHttp.NetshotBadRequestException("Missing driver",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
            }
            if (!driver1.equals(driver2)) {
                logger.error("Incompatible configurations, {} and {} (different drivers).", id1, id2);
                throw new MappingHttp.NetshotBadRequestException("Incompatible configurations",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INCOMPATIBLE_CONFIGS);
            }

            configDiffs = new MappingHttp.RsConfigDiff(config1.getChangeDate(),
                    config2.getChangeDate());
            Map<String, ConfigAttribute> attributes1 = config1.getAttributeMap();
            Map<String, ConfigAttribute> attributes2 = config2.getAttributeMap();
            for (AttributeDefinition definition : driver1.getAttributes()) {
                if (definition.isComparable()) {
                    ConfigAttribute attribute1 = attributes1.get(definition.getName());
                    ConfigAttribute attribute2 = attributes2.get(definition.getName());
                    String text1 = (attribute1 == null ? "" : attribute1.getAsText());
                    String text2 = (attribute2 == null ? "" : attribute2.getAsText());
                    List<String> lines1 = Arrays.asList(text1.replace("\r", "").split("\n"));
                    List<String> lines2 = Arrays.asList(text2.replace("\r", "").split("\n"));
                    Patch<String> patch = DiffUtils.diff(lines1, lines2);
                    for (Delta<String> delta : patch.getDeltas()) {
                        configDiffs.addDelta(definition.getTitle(), new MappingHttp.RsConfigDelta(delta, lines1));
                    }
                }
            }
            return configDiffs;
        } catch (HibernateException e) {
            logger.error("Unable to fetch the configs", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the configs",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Gets the device.
     *
     * @param id the id
     * @return the device
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("devices/{id}")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Device getDevice(@PathParam("id") Long id)
            throws WebApplicationException {
        logger.debug("REST request, device {}.", id);
        Session session = Database.getSession();
        Device device;
        try {
            device = (Device) session
                    .createQuery("from Device d left join fetch d.credentialSets cs left join fetch d.ownerGroups g left join fetch d.complianceCheckResults left join fetch d.attributes where d.id = :id")
                    .setLong("id", id)
                    .uniqueResult();
            if (device == null) {
                throw new MappingHttp.NetshotBadRequestException("Can't find this device",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DEVICE);
            }
            device.setMgmtDomain(Database.unproxy(device.getMgmtDomain()));
            device.setEolModule(Database.unproxy(device.getEolModule()));
            device.setEosModule(Database.unproxy(device.getEosModule()));
        } catch (HibernateException e) {
            logger.error("Unable to fetch the device", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the device",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
        return device;
    }

    /**
     * Gets the devices.
     *
     * @return the devices
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("devices")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<MappingHttp.RsLightDevice> getDevices() throws WebApplicationException {
        logger.debug("REST request, devices.");
        Session session = Database.getSession();
        try {
            @SuppressWarnings("unchecked")
            List<MappingHttp.RsLightDevice> devices = session.createQuery(DEVICELIST_BASEQUERY + "from Device d")
                    .setResultTransformer(Transformers.aliasToBean(MappingHttp.RsLightDevice.class))
                    .list();
            return devices;
        } catch (HibernateException e) {
            logger.error("Unable to fetch the devices", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the devices",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Gets the device types.
     *
     * @return the device types
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("devicetypes")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<DeviceDriver> getDeviceTypes() throws WebApplicationException {
        logger.debug("REST request, device types.");
        List<DeviceDriver> deviceTypes = new ArrayList<DeviceDriver>();
        deviceTypes.addAll(DeviceDriver.getAllDrivers());
        return deviceTypes;
    }

    @GET
    @Path("refresheddevicetypes")
    @RolesAllowed("admin")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<DeviceDriver> getDeviceTypesAndRefresh() throws WebApplicationException {
        logger.debug("REST request, refresh and get device types.");
        try {
            DeviceDriver.refreshDrivers();
        } catch (Exception e) {
            logger.error("Error in REST service while refreshing the device types.", e);
        }
        return this.getDeviceTypes();
    }

    /**
     * Gets the device families.
     *
     * @return the device families
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("devicefamilies")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<MappingHttp.RsDeviceFamily> getDeviceFamilies() throws WebApplicationException {
        logger.debug("REST request, device families.");
        Session session = Database.getSession();
        try {
            @SuppressWarnings("unchecked")
            List<MappingHttp.RsDeviceFamily> deviceFamilies = session
                    .createQuery("select distinct d.driver as driver, d.family as deviceFamily from Device d")
                    .setResultTransformer(Transformers.aliasToBean(MappingHttp.RsDeviceFamily.class))
                    .list();
            return deviceFamilies;
        } catch (HibernateException e) {
            logger.error("Error while loading device families.", e);
            throw new MappingHttp.NetshotBadRequestException("Database error",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Gets the known part numbers.
     *
     * @return the part numbers
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("partnumbers")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<MappingHttp.RsPartNumber> getPartNumbers() throws WebApplicationException {
        logger.debug("REST request, dpart numbers.");
        Session session = Database.getSession();
        try {
            @SuppressWarnings("unchecked")
            List<MappingHttp.RsPartNumber> partNumbers = session
                    .createQuery("select distinct m.partNumber as partNumber from Module m")
                    .setResultTransformer(Transformers.aliasToBean(MappingHttp.RsPartNumber.class))
                    .list();
            return partNumbers;
        } catch (HibernateException e) {
            logger.error("Error while loading part numbers.", e);
            throw new MappingHttp.NetshotBadRequestException("Database error",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Adds the device.
     *
     * @param device the device
     * @return the task
     * @throws WebApplicationException the web application exception
     */
    @SuppressWarnings("unchecked")
    @POST
    @Path("devices")
    @RolesAllowed("readwrite")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Task addDevice(@Context HttpServletRequest request, MappingHttp.RsNewDevice device) throws WebApplicationException {
        logger.debug("REST request, new device.");
        Network4Address deviceAddress;
        try {
            deviceAddress = new Network4Address(device.getIpAddress());
            if (!deviceAddress.isNormalUnicast()) {
                logger.warn("User posted an invalid IP address (not normal unicast).");
                throw new MappingHttp.NetshotBadRequestException("Invalid IP address",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_IP_ADDRESS);
            }
        } catch (UnknownHostException e) {
            logger.warn("User posted an invalid IP address.");
            throw new MappingHttp.NetshotBadRequestException("Malformed IP address",
                    MappingHttp.NetshotBadRequestException.NETSHOT_MALFORMED_IP_ADDRESS);
        }
        Domain domain;
        List<DeviceCredentialSet> knownCommunities;
        Session session = Database.getSession();
        try {
            logger.debug("Looking for an existing device with this IP address.");
            Device duplicate = (Device) session
                    .createQuery("from Device d where d.mgmtAddress.address = :ip")
                    .setInteger("ip", deviceAddress.getIntAddress()).uniqueResult();
            if (duplicate != null) {
                logger.error("Device {} is already present with this IP address.",
                        duplicate.getId());
                throw new MappingHttp.NetshotBadRequestException(String.format(
                        "The device '%s' already exists with this IP address.",
                        duplicate.getName()),
                        MappingHttp.NetshotBadRequestException.NETSHOT_DUPLICATE_DEVICE);
            }
            domain = (Domain) session.load(Domain.class, device.getDomainId());
            knownCommunities = session
                    .createQuery("from DeviceSnmpCommunity c where c.mgmtDomain is null or c.mgmtDomain = :domain")
                    .setEntity("domain", domain)
                    .list();
            if (knownCommunities.size() == 0) {
                logger.error("No available SNMP community");
                throw new MappingHttp.NetshotBadRequestException(
                        "There is no known SNMP community in the database to poll the device.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_CREDENTIALS_NOTFOUND);
            }
        } catch (ObjectNotFoundException e) {
            logger.error("Non existing domain.", e);
            throw new MappingHttp.NetshotBadRequestException("Invalid domain",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DOMAIN);
        } catch (HibernateException e) {
            logger.error("Error while loading domain or communities.", e);
            throw new MappingHttp.NetshotBadRequestException("Database error",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
        User user = (User) request.getSession().getAttribute("user");
        if (device.isAutoDiscover()) {
            try {
                DiscoverDeviceTypeTask task = new DiscoverDeviceTypeTask(deviceAddress, domain,
                        String.format("Device added by %s", user.getUsername()), user.getUsername());
                task.setComments(String.format("Autodiscover device %s",
                        deviceAddress.getIp()));
                for (DeviceCredentialSet credentialSet : knownCommunities) {
                    task.addCredentialSet(credentialSet);
                }
                TaskManager.addTask(task);
                return task;
            } catch (SchedulerException e) {
                logger.error("Unable to schedule the discovery task.", e);
                throw new MappingHttp.NetshotBadRequestException("Unable to schedule the task",
                        MappingHttp.NetshotBadRequestException.NETSHOT_SCHEDULE_ERROR);
            } catch (HibernateException e) {
                logger.error("Error while adding the discovery task.", e);
                throw new MappingHttp.NetshotBadRequestException("Database error",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
            }
        } else {
            DeviceDriver driver = DeviceDriver.getDriverByName(device.getDeviceType());
            if (driver == null) {
                logger.warn("Invalid posted device driver.");
                throw new MappingHttp.NetshotBadRequestException("Invalid device type.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DEVICE_CLASSNAME);
            }
            session = Database.getSession();
            TakeSnapshotTask task;
            Device newDevice = null;

            if (!Objects.equals(device.getPathConfiguration(), "/")) {
                String path = Netshot.getConfig("netshot.snapshots.dump");
                String pathTmp = path + device.getPathConfiguration();
                try {
                    File f = new File(pathTmp);
                    if (!f.exists() || (f.exists() && !f.isDirectory()))
                        f.mkdirs();
                    else
                        device.setPathConfiguration(path);
                } catch (Exception e) {
                    logger.error("Error while creating the directory " + pathTmp + " ", e);
                    throw new MappingHttp.NetshotBadRequestException("File Error : " + pathTmp,
                            MappingHttp.NetshotBadRequestException.NETSHOT_ERROR_CREATEFOLDER);
                }
            }
            try {
                session.beginTransaction();
                newDevice = new Device(driver.getName(), deviceAddress, domain, user.getUsername(), device.getPathConfiguration(), device.getEmails(), device.getOnSuccess(), device.getOnError());
                session.save(newDevice);
                task = new TakeSnapshotTask(newDevice, "Initial snapshot after device creation", user.getUsername());
                session.save(task);
                session.getTransaction().commit();
            } catch (Exception e) {
                session.getTransaction().rollback();
                logger.error("Error while creating the device", e);
                throw new MappingHttp.NetshotBadRequestException("Database error",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
            } finally {
                session.close();
            }
            if (newDevice != null) {
                DynamicDeviceGroup.refreshAllGroups(newDevice);
            }
            try {
                TaskManager.addTask(task);
                return task;
            } catch (HibernateException e) {
                logger.error("Unable to add the task.", e);
                throw new MappingHttp.NetshotBadRequestException(
                        "Unable to add the task to the database.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
            } catch (SchedulerException e) {
                logger.error("Unable to schedule the task.", e);
                throw new MappingHttp.NetshotBadRequestException("Unable to schedule the task.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_SCHEDULE_ERROR);
            }
        }

    }

    /**
     * Delete device.
     *
     * @param id the id
     * @throws WebApplicationException the web application exception
     */
    @DELETE
    @Path("devices/{id}")
    @RolesAllowed("readwrite")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public void deleteDevice(@PathParam("id") Long id)
            throws WebApplicationException {
        logger.debug("REST request, delete device {}.", id);
        Session session = Database.getSession();
        try {
            session.beginTransaction();
            Device device = (Device) session.load(Device.class, id);
            for (DeviceGroup group : device.getOwnerGroups()) {
                group.deleteCachedDevice(device);
            }
            session.delete(device);
            session.getTransaction().commit();
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Unable to delete the device {}.", id, e);
            Throwable t = e.getCause();
            if (t != null && t.getMessage().contains("foreign key constraint fails")) {
                throw new MappingHttp.NetshotBadRequestException(
                        "Unable to delete the device, there must be other objects using it.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_USED_DEVICE);
            }
            throw new MappingHttp.NetshotBadRequestException("Unable to delete the device",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Sets the device.
     *
     * @param request  the request
     * @param id       the id
     * @param rsDevice the rs device
     * @return the device
     * @throws WebApplicationException the web application exception
     */
    @PUT
    @Path("devices/{id}")
    @RolesAllowed("readwrite")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Device setDevice(@Context HttpServletRequest request, @PathParam("id") Long id, MappingHttp.RsDevice rsDevice)
            throws WebApplicationException {
        logger.debug("REST request, edit device {}.", id);
        Device device;
        Session session = Database.getSession();
        try {
            session.beginTransaction();
            device = (Device) session.load(Device.class, id);
            if (rsDevice.isEnabled() != null) {
                if (rsDevice.isEnabled()) {
                    device.setStatus(Status.INPRODUCTION);
                } else {
                    device.setStatus(Status.DISABLED);
                }
            }
            if (rsDevice.getIpAddress() != null) {
                Network4Address v4Address = new Network4Address(rsDevice.getIpAddress());
                if (!v4Address.isNormalUnicast()) {
                    session.getTransaction().rollback();
                    throw new MappingHttp.NetshotBadRequestException("Invalid IP address",
                            MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_IP_ADDRESS);
                }
                device.setMgmtAddress(v4Address);
            }
            if (rsDevice.getComments() != null) {
                device.setComments(rsDevice.getComments());
            }
            if (rsDevice.getCredentialSetIds() != null) {
                if (rsDevice.getClearCredentialSetIds() == null) {
                    device.clearCredentialSets();
                } else {
                    Iterator<DeviceCredentialSet> csIterator = device.getCredentialSets().iterator();
                    while (csIterator.hasNext()) {
                        if (rsDevice.getClearCredentialSetIds().contains(csIterator.next().getId())) {
                            csIterator.remove();
                        }
                    }
                }
                for (Long credentialSetId : rsDevice.getCredentialSetIds()) {
                    try {
                        DeviceCredentialSet credentialSet = (DeviceCredentialSet) session
                                .load(DeviceCredentialSet.class, credentialSetId);
                        device.addCredentialSet(credentialSet);
                    } catch (ObjectNotFoundException e) {
                        logger.error("Non existing credential set {}.", credentialSetId);
                    }
                }
            }
            if (rsDevice.isAutoTryCredentials() != null) {
                device.setAutoTryCredentials(rsDevice.isAutoTryCredentials());
            }
            if (rsDevice.getMgmtDomain() != null) {
                Domain domain = (Domain) session.load(Domain.class, rsDevice.getMgmtDomain());
                device.setMgmtDomain(domain);
            }

            if (!Objects.equals(rsDevice.getPathConfiguration(), "/")) {
                String path = Netshot.getConfig("netshot.snapshots.dump");
                String pathTmp = path + rsDevice.getPathConfiguration();
                try {
                    File f = new File(pathTmp);
                    if (!f.exists() || (f.exists() && !f.isDirectory())) {
                        f.mkdirs();
                        device.setPath(rsDevice.getPathConfiguration());
                        VirtualDevice.setPermFolder(f.toPath());
                    } else
                        device.setPath(rsDevice.getPathConfiguration());
                } catch (Exception e) {
                    logger.error("Error while creating the directory " + pathTmp + " ", e);
                    throw new MappingHttp.NetshotBadRequestException("File Error : " + pathTmp,
                            MappingHttp.NetshotBadRequestException.NETSHOT_ERROR_CREATEFOLDER);
                }
            }
            device.setEmails(rsDevice.getEmails());
            device.setOnError(rsDevice.getOnError());
            device.setOnSuccess(rsDevice.getOnSuccess());
            session.update(device);
            session.getTransaction().commit();
        } catch (UnknownHostException e) {
            session.getTransaction().rollback();
            logger.warn("User posted an invalid IP address.", e);
            throw new MappingHttp.NetshotBadRequestException("Malformed IP address",
                    MappingHttp.NetshotBadRequestException.NETSHOT_MALFORMED_IP_ADDRESS);
        } catch (ObjectNotFoundException e) {
            session.getTransaction().rollback();
            logger.error("The device doesn't exist.", e);
            throw new MappingHttp.NetshotBadRequestException("The device doesn't exist anymore.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DEVICE);
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Cannot edit the device.", e);
            Throwable t = e.getCause();
            if (t != null && t.getMessage().contains("Duplicate entry")) {
                throw new MappingHttp.NetshotBadRequestException(
                        "A device with this IP address already exists.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DUPLICATE_DEVICE);
            }
            if (t != null && t.getMessage().contains("domain")) {
                throw new MappingHttp.NetshotBadRequestException("Unable to find the domain",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DOMAIN);
            }
            throw new MappingHttp.NetshotBadRequestException("Unable to save the device.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
        DynamicDeviceGroup.refreshAllGroups(device);
        return this.getDevice(id);
    }

    /**
     * Gets the task.
     *
     * @param id the id
     * @return the task
     */
    @GET
    @Path("tasks/{id}")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Task getTask(@PathParam("id") Long id) {
        logger.debug("REST request, get task {}", id);
        Session session = Database.getSession();
        Task task;
        try {
            task = (Task) session.get(Task.class, id);
            return task;
        } catch (ObjectNotFoundException e) {
            logger.error("Unable to find the task {}.", id, e);
            throw new MappingHttp.NetshotBadRequestException("Task not found",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_TASK);
        } catch (HibernateException e) {
            logger.error("Unable to fetch the task {}.", id, e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the task",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Gets the tasks.
     *
     * @return the tasks
     */
    @GET
    @Path("tasks")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<Task> getTasks() {
        logger.debug("REST request, get tasks.");
        Session session = Database.getSession();
        try {
            @SuppressWarnings("unchecked")
            List<Task> tasks = session.createQuery("from Task t order by t.id desc")
                    .list();
            return tasks;
        } catch (HibernateException e) {
            logger.error("Unable to fetch the tasks.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the tasks",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Gets the credential sets.
     *
     * @return the credential sets
     * @throws WebApplicationException the web application exception
     */
    @SuppressWarnings("unchecked")
    @GET
    @Path("credentialsets")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<DeviceCredentialSet> getCredentialSets()
            throws WebApplicationException {
        logger.debug("REST request, get credentials.");
        Session session = Database.getSession();
        List<DeviceCredentialSet> credentialSets;
        try {
            credentialSets = session.createCriteria(DeviceCredentialSet.class).list();
        } catch (HibernateException e) {
            logger.error("Unable to fetch the credentials.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the credentials",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
        for (DeviceCredentialSet credentialSet : credentialSets) {
            if (DeviceCliAccount.class.isInstance(credentialSet)) {
                ((DeviceCliAccount) credentialSet).setPassword("=");
                ((DeviceCliAccount) credentialSet).setSuperPassword("=");
            }
        }
        return credentialSets;
    }

    /**
     * Delete credential set.
     *
     * @param id the id
     * @throws WebApplicationException the web application exception
     */
    @DELETE
    @Path("credentialsets/{id}")
    @RolesAllowed("admin")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public void deleteCredentialSet(@PathParam("id") Long id)
            throws WebApplicationException {
        logger.debug("REST request, delete credentials {}", id);
        Session session = Database.getSession();
        try {
            session.beginTransaction();
            DeviceCredentialSet credentialSet = (DeviceCredentialSet) session.load(
                    DeviceCredentialSet.class, id);
            session.delete(credentialSet);
            session.getTransaction().commit();
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Unable to delete the credentials {}", id, e);
            Throwable t = e.getCause();
            if (t != null && t.getMessage().contains("foreign key constraint fails")) {
                throw new MappingHttp.NetshotBadRequestException(
                        "Unable to delete the credential set, there must be devices or tasks using it.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_USED_CREDENTIALS);
            }
            throw new MappingHttp.NetshotBadRequestException(
                    "Unable to delete the credential set",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Adds the credential set.
     *
     * @param request       the request
     * @param credentialSet the credential set
     * @return the device credential set
     * @throws WebApplicationException the web application exception
     */
    @POST
    @Path("credentialsets")
    @RolesAllowed("admin")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public void addCredentialSet(DeviceCredentialSet credentialSet)
            throws WebApplicationException {
        logger.debug("REST request, add credentials.");
        Session session = Database.getSession();
        try {
            session.beginTransaction();
            if (credentialSet.getMgmtDomain() != null) {
                credentialSet.setMgmtDomain((Domain) session.load(Domain.class, credentialSet.getMgmtDomain().getId()));
            }
            session.save(credentialSet);
            session.getTransaction().commit();
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            Throwable t = e.getCause();
            logger.error("Can't add the credentials.", e);
            if (t != null && t.getMessage().contains("Duplicate entry")) {
                throw new MappingHttp.NetshotBadRequestException(
                        "A credential set with this name already exists.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DUPLICATE_CREDENTIALS);
            } else if (t != null && t.getMessage().contains("mgmt_domain")) {
                throw new MappingHttp.NetshotBadRequestException(
                        "The domain doesn't exist.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DOMAIN);
            }
            throw new MappingHttp.NetshotBadRequestException("Unable to save the credential set",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Sets the credential set.
     *
     * @param request         the request
     * @param id              the id
     * @param rsCredentialSet the rs credential set
     * @return the device credential set
     * @throws WebApplicationException the web application exception
     */
    @PUT
    @Path("credentialsets/{id}")
    @RolesAllowed("admin")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public DeviceCredentialSet setCredentialSet(@PathParam("id") Long id,
                                                DeviceCredentialSet rsCredentialSet) throws WebApplicationException {
        logger.debug("REST request, edit credentials {}", id);
        Session session = Database.getSession();
        DeviceCredentialSet credentialSet;
        try {
            session.beginTransaction();
            credentialSet = (DeviceCredentialSet) session.get(
                    rsCredentialSet.getClass(), id);
            if (credentialSet == null) {
                logger.error("Unable to find the credential set {}.", id);
                throw new MappingHttp.NetshotBadRequestException(
                        "Unable to find the credential set.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_CREDENTIALS_NOTFOUND);
            }
            if (!credentialSet.getClass().equals(rsCredentialSet.getClass())) {
                logger.error("Wrong posted credential type for credential set {}.", id);
                throw new MappingHttp.NetshotBadRequestException(
                        "The posted credential type doesn't match the existing one.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_CREDENTIALS_TYPE);
            }
            if (rsCredentialSet.getMgmtDomain() == null) {
                credentialSet.setMgmtDomain(null);
            } else {
                credentialSet.setMgmtDomain((Domain) session.load(Domain.class, rsCredentialSet.getMgmtDomain().getId()));
            }
            credentialSet.setName(rsCredentialSet.getName());
            if (DeviceCliAccount.class.isInstance(credentialSet)) {
                DeviceCliAccount cliAccount = (DeviceCliAccount) credentialSet;
                DeviceCliAccount rsCliAccount = (DeviceCliAccount) rsCredentialSet;
                cliAccount.setUsername(rsCliAccount.getUsername());
                if (!rsCliAccount.getPassword().equals("=")) {
                    cliAccount.setPassword(rsCliAccount.getPassword());
                }
                if (!rsCliAccount.getSuperPassword().equals("=")) {
                    cliAccount.setSuperPassword(rsCliAccount.getSuperPassword());
                }
                if (DeviceSshKeyAccount.class.isInstance(credentialSet)) {
                    ((DeviceSshKeyAccount) cliAccount).setPublicKey(((DeviceSshKeyAccount) rsCliAccount).getPublicKey());
                    ((DeviceSshKeyAccount) cliAccount).setPrivateKey(((DeviceSshKeyAccount) rsCliAccount).getPrivateKey());
                }
            } else if (DeviceSnmpCommunity.class.isInstance(credentialSet)) {
                ((DeviceSnmpCommunity) credentialSet)
                        .setCommunity(((DeviceSnmpCommunity) rsCredentialSet)
                                .getCommunity());
            }
            session.update(credentialSet);
            session.getTransaction().commit();
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            Throwable t = e.getCause();
            logger.error("Unable to save the credentials {}.", id, e);
            if (t != null && t.getMessage().contains("Duplicate entry")) {
                throw new MappingHttp.NetshotBadRequestException(
                        "A credential set with this name already exists.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DUPLICATE_CREDENTIALS);
            } else if (t != null && t.getMessage().contains("mgmt_domain")) {
                throw new MappingHttp.NetshotBadRequestException(
                        "The domain doesn't exist.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DOMAIN);
            }
            throw new MappingHttp.NetshotBadRequestException("Unable to save the credential set",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } catch (MappingHttp.NetshotBadRequestException e) {
            session.getTransaction().rollback();
            throw e;
        } finally {
            session.close();
        }
        return credentialSet;
    }

    /**
     * Search devices.
     *
     * @param request  the request
     * @param criteria the criteria
     * @return the rs search results
     * @throws WebApplicationException the web application exception
     */
    @POST
    @Path("devices/search")
    @RolesAllowed("readonly")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public MappingHttp.RsSearchResults searchDevices(MappingHttp.RsSearchCriteria criteria)
            throws WebApplicationException {
        logger.debug("REST request, search devices, query '{}', driver '{}'.",
                criteria.getQuery(), criteria.getDriver());

        DeviceDriver driver = DeviceDriver.getDriverByName(criteria.getDriver());
        try {
            Finder finder = new Finder(criteria.getQuery(), driver);
            Session session = Database.getSession();
            try {
                Query query = session.createQuery(DEVICELIST_BASEQUERY
                        + finder.getHql());
                finder.setVariables(query);
                @SuppressWarnings("unchecked")
                List<MappingHttp.RsLightDevice> devices = query
                        .setResultTransformer(Transformers.aliasToBean(MappingHttp.RsLightDevice.class))
                        .list();
                MappingHttp.RsSearchResults results = new MappingHttp.RsSearchResults();
                results.setDevices(devices);
                results.setQuery(finder.getFormattedQuery());
                return results;
            } catch (HibernateException e) {
                logger.error("Error while searching for the devices.", e);
                throw new MappingHttp.NetshotBadRequestException("Unable to fetch the devices",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
            } finally {
                session.close();
            }
        } catch (FinderParseException e) {
            logger.warn("User's query is invalid.", e);
            throw new MappingHttp.NetshotBadRequestException("Invalid search string. "
                    + e.getMessage(),
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_SEARCH_STRING);
        }
    }

    /**
     * Adds the group.
     *
     * @param request     the request
     * @param deviceGroup the device group
     * @return the device group
     * @throws WebApplicationException the web application exception
     */
    @POST
    @Path("groups")
    @RolesAllowed("readwrite")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public DeviceGroup addGroup(DeviceGroup deviceGroup)
            throws WebApplicationException {
        logger.debug("REST request, add group.");
        String name = deviceGroup.getName().trim();
        if (name.isEmpty()) {
            logger.warn("User posted an empty group name.");
            throw new MappingHttp.NetshotBadRequestException("Invalid group name.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_GROUP_NAME);
        }
        deviceGroup.setName(name);
        deviceGroup.setId(0);
        Session session = Database.getSession();
        try {
            session.beginTransaction();
            session.save(deviceGroup);
            session.getTransaction().commit();
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Error while saving the new device group.", e);
            Throwable t = e.getCause();
            if (t != null && t.getMessage().contains("Duplicate entry")) {
                throw new MappingHttp.NetshotBadRequestException(
                        "A group with this name already exists.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DUPLICATE_GROUP);
            }
            throw new MappingHttp.NetshotBadRequestException(
                    "Unable to add the group to the database",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
        return deviceGroup;
    }

    /**
     * Gets the groups.
     *
     * @return the groups
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("groups")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<DeviceGroup> getGroups() throws WebApplicationException {
        logger.debug("REST request, get groups.");
        Session session = Database.getSession();
        try {
            @SuppressWarnings("unchecked")
            List<DeviceGroup> deviceGroups = session
                    .createCriteria(DeviceGroup.class).list();
            return deviceGroups;
        } catch (HibernateException e) {
            logger.error("Unable to fetch the groups.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the groups",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Delete group.
     *
     * @param id the id
     * @throws WebApplicationException the web application exception
     */
    @DELETE
    @Path("groups/{id}")
    @RolesAllowed("readwrite")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public void deleteGroup(@PathParam("id") Long id)
            throws WebApplicationException {
        logger.debug("REST request, delete group {}.", id);
        Session session = Database.getSession();
        try {
            session.beginTransaction();
            DeviceGroup deviceGroup = (DeviceGroup) session.load(DeviceGroup.class, id);
            for (Policy policy : deviceGroup.getAppliedPolicies()) {
                policy.setTargetGroup(null);
                session.save(policy);
            }
            session.delete(deviceGroup);
            session.getTransaction().commit();
        } catch (ObjectNotFoundException e) {
            session.getTransaction().rollback();
            logger.error("The group {} to be deleted doesn't exist.", id, e);
            throw new MappingHttp.NetshotBadRequestException("The group doesn't exist.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_GROUP);
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Unable to delete the group {}.", id, e);
            throw new MappingHttp.NetshotBadRequestException("Unable to delete the group",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Sets the group.
     *
     * @param id      the id
     * @param rsGroup the rs group
     * @return the device group
     * @throws WebApplicationException the web application exception
     */
    @PUT
    @Path("groups/{id}")
    @RolesAllowed("readwrite")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public DeviceGroup setGroup(@PathParam("id") Long id, MappingHttp.RsDeviceGroup rsGroup)
            throws WebApplicationException {
        logger.debug("REST request, edit group {}.", id);
        Session session = Database.getSession();
        try {
            session.beginTransaction();
            DeviceGroup group = (DeviceGroup) session.get(DeviceGroup.class, id);
            if (group == null) {
                logger.error("Unable to find the group {} to be edited.", id);
                throw new MappingHttp.NetshotBadRequestException("Unable to find this group.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_GROUP);
            }
            if (group instanceof StaticDeviceGroup) {
                StaticDeviceGroup staticGroup = (StaticDeviceGroup) group;
                Set<Device> devices = new HashSet<Device>();
                for (Long deviceId : rsGroup.getStaticDevices()) {
                    Device device = (Device) session.load(Device.class, deviceId);
                    devices.add(device);
                }
                staticGroup.updateCachedDevices(devices);
            } else if (group instanceof DynamicDeviceGroup) {
                DynamicDeviceGroup dynamicGroup = (DynamicDeviceGroup) group;
                dynamicGroup.setDriver(rsGroup.getDriver());
                dynamicGroup.setQuery(rsGroup.getQuery());
                try {
                    dynamicGroup.refreshCache(session);
                } catch (FinderParseException e) {
                    throw new MappingHttp.NetshotBadRequestException(
                            "Invalid query for the group definition.",
                            MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DYNAMICGROUP_QUERY);
                }
            } else {
                throw new MappingHttp.NetshotBadRequestException("Unknown group type.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INCOMPATIBLE_GROUP_TYPE);
            }
            group.setFolder(rsGroup.getFolder());
            group.setHiddenFromReports(rsGroup.isHiddenFromReports());
            session.update(group);
            session.getTransaction().commit();
            return group;
        } catch (ObjectNotFoundException e) {
            session.getTransaction().rollback();
            logger.error("Unable to find a device while editing group {}.", id, e);
            throw new MappingHttp.NetshotBadRequestException(
                    "Unable to find a device. Refresh and try again.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DEVICE_IN_STATICGROUP);
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Unable to save the group {}.", id, e);
            throw new MappingHttp.NetshotBadRequestException("Unable to save the group.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } catch (WebApplicationException e) {
            session.getTransaction().rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    /**
     * Gets the group devices.
     *
     * @param id the id
     * @return the group devices
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("devices/group/{id}")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<MappingHttp.RsLightDevice> getGroupDevices(@PathParam("id") Long id)
            throws WebApplicationException {
        logger.debug("REST request, get devices from group {}.", id);
        Session session = Database.getSession();
        DeviceGroup group;
        try {
            group = (DeviceGroup) session.get(DeviceGroup.class, id);
            if (group == null) {
                logger.error("Unable to find the group {}.", id);
                throw new MappingHttp.NetshotBadRequestException("Can't find this group",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_GROUP);
            }
            Query query = session.createQuery(
                    RestService.DEVICELIST_BASEQUERY
                            + "from Device d join d.ownerGroups g where g.id = :id").setLong(
                    "id", id);
            @SuppressWarnings("unchecked")
            List<MappingHttp.RsLightDevice> devices = query
                    .setResultTransformer(Transformers.aliasToBean(MappingHttp.RsLightDevice.class))
                    .list();
            return devices;
        } catch (HibernateException e) {
            logger.error("Unable to fetch the devices of group {}.", id, e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the devices",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Sets the task.
     *
     * @param id     the id
     * @param rsTask the rs task
     * @return the task
     * @throws WebApplicationException the web application exception
     */
    @PUT
    @Path("tasks/{id}")
    @RolesAllowed("readwrite")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Task setTask(@PathParam("id") Long id, MappingHttp.RsTask rsTask)
            throws WebApplicationException {
        logger.debug("REST request, edit task {}.", id);
        Task task = null;
        Session session = Database.getSession();
        try {
            task = (Task) session.get(Task.class, id);
        } catch (HibernateException e) {
            logger.error("Unable to fetch the task {}.", id, e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the task.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }

        if (task == null) {
            logger.error("Unable to find the task {}.", id);
            throw new MappingHttp.NetshotBadRequestException("Unable to find the task.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_TASK);
        }

        if (rsTask.isCancelled()) {
            if (task.getStatus() != Task.Status.SCHEDULED) {
                logger.error("User is trying to cancel task {} not in SCHEDULE state.",
                        id);
                throw new MappingHttp.NetshotBadRequestException(
                        "The task isn't in 'SCHEDULED' state.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_TASK_NOT_CANCELLABLE);
            }

            try {
                TaskManager.cancelTask(task, "Task manually cancelled by user."); //TODO
            } catch (Exception e) {
                logger.error("Unable to cancel the task {}.", id, e);
                throw new MappingHttp.NetshotBadRequestException("Cannot cancel the task.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_TASK_CANCEL_ERROR);
            }
        }

        return task;
    }

    /**
     * Search tasks.
     *
     * @param request  the request
     * @param criteria the criteria
     * @return the list
     * @throws WebApplicationException the web application exception
     */
    @POST
    @Path("tasks/search")
    @RolesAllowed("readonly")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<Task> searchTasks(MappingHttp.RsTaskCriteria criteria)
            throws WebApplicationException {

        logger.debug("REST request, search for tasks.");

        Session session = Database.getSession();
        try {
            Criteria c = session.createCriteria(Task.class);
            Task.Status status = null;
            try {
                if (!"ANY".equals(criteria.getStatus())) {
                    status = Task.Status.valueOf(criteria.getStatus());
                    c.add(Property.forName("status").eq(status));
                }
            } catch (Exception e) {
                logger.warn("Invalid status {}.", criteria.getStatus());
            }
            Calendar min = Calendar.getInstance();
            min.setTime(criteria.getDay());
            min.set(Calendar.HOUR_OF_DAY, 0);
            min.set(Calendar.MINUTE, 0);
            min.set(Calendar.SECOND, 0);
            min.set(Calendar.MILLISECOND, 0);
            Calendar max = (Calendar) min.clone();
            max.add(Calendar.DAY_OF_MONTH, 1);

            if (status == Task.Status.SUCCESS || status == Task.Status.FAILURE) {
                c.add(Property.forName("executionDate").between(min.getTime(),
                        max.getTime()));
            } else if (status == Task.Status.CANCELLED) {
                c.add(Property.forName("changeDate").between(min.getTime(),
                        max.getTime()));
            } else if (status == null) {
                c.add(Restrictions.or(
                        Property.forName("status").eq(Task.Status.RUNNING),
                        Property.forName("status").eq(Task.Status.SCHEDULED),
                        Property.forName("executionDate").between(min.getTime(),
                                max.getTime()), Restrictions.and(
                                Property.forName("executionDate").isNull(),
                                Property.forName("changeDate").between(min.getTime(),
                                        max.getTime()))));
            }
            c.addOrder(Property.forName("id").desc());

            @SuppressWarnings("unchecked")
            List<Task> tasks = c.list();
            return tasks;
        } catch (HibernateException e) {
            logger.error("Error while searching for tasks.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the tasks",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Adds the task.
     *
     * @param rsTask the rs task
     * @return the task
     * @throws WebApplicationException the web application exception
     */
    @POST
    @Path("tasks")
    @RolesAllowed("readwrite")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Task addTask(@Context HttpServletRequest request,
                        @Context SecurityContext securityContext,
                        MappingHttp.RsTask rsTask) throws WebApplicationException {
        logger.debug("REST request, add task.");
        User user = (User) request.getSession().getAttribute("user");
        String userName = "";
        try {
            userName = user.getUsername();
        } catch (Exception e) {
        }

        Task task;
        switch (rsTask.getType()) {
            case "TakeSnapshotTask": {
                logger.trace("Adding a TakeSnapshotTask");
                Device device;
                Session session = Database.getSession();
                try {
                    device = (Device) session.get(Device.class, rsTask.getDevice());
                    if (device == null) {
                        logger.error("Unable to find the device {}.", rsTask.getDevice());
                        throw new MappingHttp.NetshotBadRequestException("Unable to find the device.",
                                MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DEVICE);
                    }
                } catch (HibernateException e) {
                    logger.error("Error while retrieving the device.", e);
                    throw new MappingHttp.NetshotBadRequestException("Database error.",
                            MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
                } finally {
                    session.close();
                }
                task = new TakeSnapshotTask(device, rsTask.getComments(), userName);
                break;
            }
            case "RunDeviceScriptTask": {
                if (!securityContext.isUserInRole("admin")) {
                    throw new MappingHttp.NetshotNotAuthorizedException("Must be admin to run scripts on devices.", 0);
                }
                logger.trace("Adding a RunDeviceScriptTask");
                DeviceDriver driver = DeviceDriver.getDriverByName(rsTask.getDriver());
                if (driver == null) {
                    logger.error("Unknown device driver {}.", rsTask.getType());
                    throw new MappingHttp.NetshotBadRequestException("Unknown device driver.",
                            MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DEVICE);
                }
                if (rsTask.getScript() == null) {
                    logger.error("The script can't be empty.");
                    throw new MappingHttp.NetshotBadRequestException("The script can't be empty.",
                            MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DEVICE);
                }
                Device device;
                Session session = Database.getSession();
                try {
                    device = (Device) session.get(Device.class, rsTask.getDevice());
                    if (device == null) {
                        logger.error("Unable to find the device {}.", rsTask.getDevice());
                        throw new MappingHttp.NetshotBadRequestException("Unable to find the device.",
                                MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DEVICE);
                    }
                } catch (HibernateException e) {
                    logger.error("Error while retrieving the device.", e);
                    throw new MappingHttp.NetshotBadRequestException("Database error.",
                            MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
                } finally {
                    session.close();
                }
                task = new RunDeviceScriptTask(device, rsTask.getScript(), driver, rsTask.getComments(), userName);
                break;
            }
            case "RunDeviceGroupScriptTask": {
                logger.trace("Adding a RunDeviceGroupScriptTask");
                DeviceDriver driver = DeviceDriver.getDriverByName(rsTask.getDriver());
                if (driver == null) {
                    logger.error("Unknown device driver {}.", rsTask.getType());
                    throw new MappingHttp.NetshotBadRequestException("Unknown device driver.",
                            MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DEVICE);
                }
                if (rsTask.getScript() == null) {
                    logger.error("The script can't be empty.");
                    throw new MappingHttp.NetshotBadRequestException("The script can't be empty.",
                            MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DEVICE);
                }
                DeviceGroup group;
                Session session = Database.getSession();
                try {
                    group = (DeviceGroup) session.get(DeviceGroup.class, rsTask.getGroup());
                    if (group == null) {
                        logger.error("Unable to find the group {}.", rsTask.getGroup());
                        throw new MappingHttp.NetshotBadRequestException("Unable to find the group.",
                                MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_GROUP);
                    }
                    task = new RunDeviceGroupScriptTask(group, rsTask.getScript(), driver, rsTask.getComments(), userName);
                } catch (HibernateException e) {
                    logger.error("Error while retrieving the group.", e);
                    throw new MappingHttp.NetshotBadRequestException("Database error.",
                            MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
                } finally {
                    session.close();
                }
                break;
            }
            case "CheckComplianceTask": {
                logger.trace("Adding a CheckComplianceTask");
                Device device;
                Session session = Database.getSession();
                try {
                    device = (Device) session.get(Device.class, rsTask.getDevice());
                    if (device == null) {
                        logger.error("Unable to find the device {}.", rsTask.getDevice());
                        throw new MappingHttp.NetshotBadRequestException("Unable to find the device.",
                                MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DEVICE);
                    }
                } catch (HibernateException e) {
                    logger.error("Error while retrieving the device.", e);
                    throw new MappingHttp.NetshotBadRequestException("Database error.",
                            MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
                } finally {
                    session.close();
                }
                task = new CheckComplianceTask(device, rsTask.getComments(), userName);
                break;
            }
            case "TakeGroupSnapshotTask": {
                logger.trace("Adding a TakeGroupSnapshotTask");
                DeviceGroup group;
                Session session = Database.getSession();
                try {
                    group = (DeviceGroup) session.get(DeviceGroup.class, rsTask.getGroup());
                    if (group == null) {
                        logger.error("Unable to find the group {}.", rsTask.getGroup());
                        throw new MappingHttp.NetshotBadRequestException("Unable to find the group.",
                                MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_GROUP);
                    }
                    task = new TakeGroupSnapshotTask(group, rsTask.getComments(), userName,
                            rsTask.getLimitToOutofdateDeviceHours());
                } catch (HibernateException e) {
                    logger.error("Error while retrieving the group.", e);
                    throw new MappingHttp.NetshotBadRequestException("Database error.",
                            MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
                } finally {
                    session.close();
                }
                break;
            }
            case "CheckGroupComplianceTask": {
                logger.trace("Adding a CheckGroupComplianceTask");
                DeviceGroup group;
                Session session = Database.getSession();
                try {
                    group = (DeviceGroup) session.get(DeviceGroup.class, rsTask.getGroup());
                    if (group == null) {
                        logger.error("Unable to find the group {}.", rsTask.getGroup());
                        throw new MappingHttp.NetshotBadRequestException("Unable to find the group.",
                                MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_GROUP);
                    }
                    task = new CheckGroupComplianceTask(group, rsTask.getComments(), userName);
                } catch (HibernateException e) {
                    logger.error("Error while retrieving the group.", e);
                    throw new MappingHttp.NetshotBadRequestException("Database error.",
                            MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
                } finally {
                    session.close();
                }
                break;
            }
            case "CheckGroupSoftwareTask": {
                logger.trace("Adding a CheckGroupSoftwareTask");
                DeviceGroup group;
                Session session = Database.getSession();
                try {
                    group = (DeviceGroup) session.get(DeviceGroup.class, rsTask.getGroup());
                    if (group == null) {
                        logger.error("Unable to find the group {}.", rsTask.getGroup());
                        throw new MappingHttp.NetshotBadRequestException("Unable to find the group.",
                                MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_GROUP);
                    }
                    task = new CheckGroupSoftwareTask(group, rsTask.getComments(), userName);
                } catch (HibernateException e) {
                    logger.error("Error while retrieving the group.", e);
                    throw new MappingHttp.NetshotBadRequestException("Database error.",
                            MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
                } finally {
                    session.close();
                }
                break;
            }
            case "ScanSubnetsTask": {
                logger.trace("Adding a ScanSubnetsTask");
                Set<Network4Address> subnets = new HashSet<Network4Address>();
                String[] rsSubnets = rsTask.getSubnets().split("(\r\n|\n|;| |,)");
                Pattern pattern = Pattern.compile("^(?<ip>[0-9\\.]+)(/(?<mask>[0-9]+))?$");
                for (String rsSubnet : rsSubnets) {
                    Matcher matcher = pattern.matcher(rsSubnet);
                    if (!matcher.find()) {
                        logger.warn("User posted an invalid subnet '{}'.", rsSubnet);
                        throw new MappingHttp.NetshotBadRequestException(String.format("Invalid subnet '%s'.", rsSubnet),
                                MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_SUBNET);
                    }
                    Network4Address subnet;
                    try {
                        int mask = 32;
                        if (matcher.group("mask") != null) {
                            mask = Integer.parseInt(matcher.group("mask"));
                        }
                        subnet = new Network4Address(matcher.group("ip"), mask);
                        subnets.add(subnet);
                    } catch (Exception e) {
                        logger.warn("User posted an invalid subnet '{}'.", rsSubnet, e);
                        throw new MappingHttp.NetshotBadRequestException(String.format("Invalid subnet '%s'.", rsSubnet),
                                MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_SUBNET);
                    }
                    if (subnet.getPrefixLength() < 22 || subnet.getPrefixLength() > 32) {
                        logger.warn("User posted an invalid prefix length {}.",
                                subnet.getPrefix());
                        throw new MappingHttp.NetshotBadRequestException(String.format("Invalid prefix length for '%s'.", rsSubnet),
                                MappingHttp.NetshotBadRequestException.NETSHOT_SCAN_SUBNET_TOO_BIG);
                    }
                }
                if (subnets.size() == 0) {
                    logger.warn("User posted an invalid subnet list '{}'.", rsTask.getSubnets());
                    throw new MappingHttp.NetshotBadRequestException(String.format("Invalid subnet list '%s'.", rsTask.getSubnets()),
                            MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_SUBNET);
                }
                Domain domain;
                if (rsTask.getDomain() == 0) {
                    logger.error("Domain {} is invalid (0).", rsTask.getDomain());
                    throw new MappingHttp.NetshotBadRequestException("Invalid domain",
                            MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DOMAIN);
                }
                Session session = Database.getSession();
                try {
                    domain = (Domain) session.load(Domain.class, rsTask.getDomain());
                } catch (Exception e) {
                    logger.error("Unable to load the domain {}.", rsTask.getDomain());
                    throw new MappingHttp.NetshotBadRequestException("Invalid domain",
                            MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DOMAIN);
                } finally {
                    session.close();
                }
                StringBuffer target = new StringBuffer();
                target.append("{");
                for (Network4Address subnet : subnets) {
                    if (target.length() > 1) {
                        target.append(", ");
                    }
                    target.append(subnet.getPrefix());
                }
                target.append("}");
                task = new ScanSubnetsTask(subnets, domain, rsTask.getComments(), target.toString(), userName);
                break;
            }
            case "PurgeDatabaseTask":
                logger.trace("Adding a PurgeDatabaseTask");
                if (rsTask.getDaysToPurge() < 2) {
                    logger.error(String.format("Invalid number of days %d for the PurgeDatabaseTask task.", rsTask.getDaysToPurge()));
                    throw new MappingHttp.NetshotBadRequestException("Invalid number of days.",
                            MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_TASK);
                }
                int configDays = rsTask.getConfigDaysToPurge();
                int configSize = rsTask.getConfigSizeToPurge();
                int configKeepDays = rsTask.getConfigKeepDays();
                if (configDays == -1) {
                    configSize = 0;
                    configKeepDays = 0;
                } else if (configDays <= 3) {
                    logger.error("The number of days of configurations to purge must be greater than 3.");
                    throw new MappingHttp.NetshotBadRequestException("The number of days of configurations to purge must be greater than 3.",
                            MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_TASK);
                } else {
                    if (configSize < 0) {
                        logger.error("The configuration size limit can't be negative.");
                        throw new MappingHttp.NetshotBadRequestException("The limit on the configuration size can't be negative.",
                                MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_TASK);
                    }
                    if (configKeepDays < 0) {
                        logger.error("The interval of days between configurations to keep can't be negative.");
                        throw new MappingHttp.NetshotBadRequestException("The number of days of configurations to purge can't be negative.",
                                MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_TASK);
                    }
                    if (configDays <= configKeepDays) {
                        logger.error("The number of days of configurations to purge must be greater than the number of days between two successive configurations to keep.");
                        throw new MappingHttp.NetshotBadRequestException("The number of days of configurations to purge must be greater than the number of days between two successive configurations to keep.",
                                MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_TASK);
                    }
                }
                task = new PurgeDatabaseTask(rsTask.getComments(), userName, rsTask.getDaysToPurge(),
                        configDays, configSize, configKeepDays);
                break;
            default:
                logger.error("User posted an invalid task type '{}'.", rsTask.getType());
                throw new MappingHttp.NetshotBadRequestException("Invalid task type.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_TASK);
        }
        if (rsTask.getScheduleReference() != null) {
            task.setScheduleReference(rsTask.getScheduleReference());
            task.setScheduleType(rsTask.getScheduleType());
            if (task.getScheduleType() == ScheduleType.AT) {
                Calendar inOneMinute = Calendar.getInstance();
                inOneMinute.add(Calendar.MINUTE, 1);
                if (task.getScheduleReference().before(inOneMinute.getTime())) {
                    logger
                            .error(
                                    "The schedule for the task occurs in less than one minute ({} vs {}).",
                                    task.getScheduleReference(), inOneMinute.getTime());
                    throw new MappingHttp.NetshotBadRequestException(
                            "The schedule occurs in the past.",
                            MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_TASK);
                }
            }
        }
        try {
            TaskManager.addTask(task);
        } catch (HibernateException e) {
            logger.error("Unable to add the task.", e);
            throw new MappingHttp.NetshotBadRequestException(
                    "Unable to add the task to the database.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } catch (SchedulerException e) {
            logger.error("Unable to schedule the task.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to schedule the task.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_SCHEDULE_ERROR);
        }
        return task;
    }

    /**
     * Gets the changes.
     *
     * @param request  the request
     * @param criteria the criteria
     * @return the changes
     * @throws WebApplicationException the web application exception
     */
    @POST
    @Path("changes")
    @RolesAllowed("readonly")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<MappingHttp.RsConfigChange> getChanges(MappingHttp.RsChangeCriteria criteria) throws WebApplicationException {
        logger.debug("REST request, config changes.");
        Session session = Database.getSession();
        try {
            @SuppressWarnings("unchecked")
            List<MappingHttp.RsConfigChange> changes = session
                    .createQuery("select c.id as newId, c.changeDate as newChangeDate, c.device.id as deviceId, c.author as author, c.device.name as deviceName from Config c where c.changeDate >= :start and c.changeDate <= :end")
                    .setTimestamp("start", criteria.getFromDate())
                    .setTimestamp("end", criteria.getToDate())
                    .setResultTransformer(Transformers.aliasToBean(MappingHttp.RsConfigChange.class))
                    .list();
            return changes;
        } catch (HibernateException e) {
            logger.error("Unable to fetch the devices", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the devices",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Gets the policies.
     *
     * @return the policies
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("policies")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<Policy> getPolicies() throws WebApplicationException {
        logger.debug("REST request, get policies.");
        Session session = Database.getSession();
        try {
            @SuppressWarnings("unchecked")
            List<Policy> policies = session.createQuery("from Policy p left join fetch p.targetGroup")
                    .list();
            return policies;
        } catch (HibernateException e) {
            logger.error("Unable to fetch the policies.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the policies",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Gets the policy rules.
     *
     * @param id the id
     * @return the policy rules
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("rules/policy/{id}")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<Rule> getPolicyRules(@PathParam("id") Long id) throws WebApplicationException {
        logger.debug("REST request, get rules for policy {}.", id);
        Session session = Database.getSession();
        try {
            Policy policy = (Policy) session.load(Policy.class, id);
            if (policy == null) {
                logger.error("Invalid policy.");
                throw new MappingHttp.NetshotBadRequestException("Invalid policy",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_POLICY);
            }
            List<Rule> rules = new ArrayList<Rule>();
            rules.addAll(policy.getRules());
            return rules;
        } catch (HibernateException e) {
            logger.error("Unable to fetch the rules.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the rules",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Adds the policy.
     *
     * @param request  the request
     * @param rsPolicy the rs policy
     * @return the policy
     * @throws WebApplicationException the web application exception
     */
    @POST
    @Path("policies")
    @RolesAllowed("readwrite")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Policy addPolicy(MappingHttp.RsPolicy rsPolicy) throws WebApplicationException {
        logger.debug("REST request, add policy.");
        String name = rsPolicy.getName().trim();
        if (name.isEmpty()) {
            logger.warn("User posted an empty policy name.");
            throw new MappingHttp.NetshotBadRequestException("Invalid policy name.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_POLICY_NAME);
        }
        Policy policy;
        Session session = Database.getSession();
        try {
            session.beginTransaction();

            DeviceGroup group = null;
            if (rsPolicy.getGroup() != -1) {
                group = (DeviceGroup) session.load(DeviceGroup.class, rsPolicy.getGroup());
            }

            policy = new Policy(name, group);

            session.save(policy);
            session.getTransaction().commit();
        } catch (ObjectNotFoundException e) {
            session.getTransaction().rollback();
            logger.error("The posted group doesn't exist", e);
            throw new MappingHttp.NetshotBadRequestException(
                    "Invalid group",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_GROUP);
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Error while saving the new policy.", e);
            Throwable t = e.getCause();
            if (t != null && t.getMessage().contains("Duplicate entry")) {
                throw new MappingHttp.NetshotBadRequestException(
                        "A policy with this name already exists.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DUPLICATE_POLICY);
            }
            throw new MappingHttp.NetshotBadRequestException(
                    "Unable to add the policy to the database",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
        return policy;
    }

    /**
     * Delete policy.
     *
     * @param id the id
     * @throws WebApplicationException the web application exception
     */
    @DELETE
    @Path("policies/{id}")
    @RolesAllowed("readwrite")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public void deletePolicy(@PathParam("id") Long id)
            throws WebApplicationException {
        logger.debug("REST request, delete policy {}.", id);
        Session session = Database.getSession();
        try {
            session.beginTransaction();
            Policy policy = (Policy) session.load(Policy.class, id);
            session.delete(policy);
            session.getTransaction().commit();
        } catch (ObjectNotFoundException e) {
            session.getTransaction().rollback();
            logger.error("The policy {} to be deleted doesn't exist.", id, e);
            throw new MappingHttp.NetshotBadRequestException("The policy doesn't exist.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_POLICY);
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Unable to delete the policy {}.", id, e);
            throw new MappingHttp.NetshotBadRequestException("Unable to delete the policy",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Sets the policy.
     *
     * @param request  the request
     * @param id       the id
     * @param rsPolicy the rs policy
     * @return the policy
     * @throws WebApplicationException the web application exception
     */
    @PUT
    @Path("policies/{id}")
    @RolesAllowed("readwrite")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Policy setPolicy(@PathParam("id") Long id, MappingHttp.RsPolicy rsPolicy)
            throws WebApplicationException {
        logger.debug("REST request, edit policy {}.", id);
        Session session = Database.getSession();
        try {
            session.beginTransaction();
            Policy policy = (Policy) session.get(Policy.class, id);
            if (policy == null) {
                logger.error("Unable to find the policy {} to be edited.", id);
                throw new MappingHttp.NetshotBadRequestException("Unable to find this policy.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_POLICY);
            }

            String name = rsPolicy.getName().trim();
            if (name.isEmpty()) {
                logger.warn("User posted an empty policy name.");
                throw new MappingHttp.NetshotBadRequestException("Invalid policy name.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_POLICY_NAME);
            }
            policy.setName(name);

            if (policy.getTargetGroup() != null && policy.getTargetGroup().getId() != rsPolicy.getGroup()) {
                session.createQuery("delete CheckResult cr where cr.key.rule in (select r from Rule r where r.policy = :id)")
                        .setLong("id", policy.getId())
                        .executeUpdate();
            }
            DeviceGroup group = null;
            if (rsPolicy.getGroup() != -1) {
                group = (DeviceGroup) session.load(DeviceGroup.class, rsPolicy.getGroup());
            }
            policy.setTargetGroup(group);

            session.update(policy);
            session.getTransaction().commit();
            return policy;
        } catch (ObjectNotFoundException e) {
            session.getTransaction().rollback();
            logger.error("Unable to find the group {} to be assigned to the policy {}.",
                    rsPolicy.getGroup(), id, e);
            throw new MappingHttp.NetshotBadRequestException(
                    "Unable to find the group.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_GROUP);
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Unable to save the policy {}.", id, e);
            Throwable t = e.getCause();
            if (t != null && t.getMessage().contains("Duplicate entry")) {
                throw new MappingHttp.NetshotBadRequestException(
                        "A policy with this name already exists.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DUPLICATE_POLICY);
            }
            throw new MappingHttp.NetshotBadRequestException("Unable to save the policy.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } catch (WebApplicationException e) {
            session.getTransaction().rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    /**
     * Adds the js rule.
     *
     * @param rsRule the rs rule
     * @return the rule
     * @throws WebApplicationException the web application exception
     */
    @POST
    @Path("rules")
    @RolesAllowed("readwrite")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Rule addRule(MappingHttp.RsRule rsRule) throws WebApplicationException {
        logger.debug("REST request, add rule.");
        if (rsRule.getName() == null || rsRule.getName().trim().isEmpty()) {
            logger.warn("User posted an empty rule name.");
            throw new MappingHttp.NetshotBadRequestException("Invalid rule name.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_RULE_NAME);
        }
        String name = rsRule.getName().trim();

        Session session = Database.getSession();
        try {
            session.beginTransaction();

            Policy policy = (Policy) session.load(Policy.class, rsRule.getPolicy());

            Rule rule;
            if (".TextRule".equals(rsRule.getType())) {
                rule = new TextRule(name, policy);
            } else {
                rule = new JavaScriptRule(name, policy);
            }

            session.save(rule);
            session.getTransaction().commit();
            return rule;
        } catch (ObjectNotFoundException e) {
            session.getTransaction().rollback();
            logger.error("The posted policy doesn't exist.", e);
            throw new MappingHttp.NetshotBadRequestException(
                    "Invalid policy.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_POLICY);
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Error while saving the new rule.", e);
            Throwable t = e.getCause();
            if (t != null && t.getMessage().contains("Duplicate entry")) {
                throw new MappingHttp.NetshotBadRequestException(
                        "A rule with this name already exists.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DUPLICATE_RULE);
            }
            throw new MappingHttp.NetshotBadRequestException(
                    "Unable to add the rule to the database",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Sets the rule.
     *
     * @param id     the id
     * @param rsRule the rs rule
     * @return the rule
     * @throws WebApplicationException the web application exception
     */
    @PUT
    @Path("rules/{id}")
    @RolesAllowed("readwrite")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Rule setRule(@PathParam("id") Long id, MappingHttp.RsRule rsRule)
            throws WebApplicationException {
        logger.debug("REST request, edit rule {}.", id);
        Session session = Database.getSession();
        try {
            session.beginTransaction();
            Rule rule = (Rule) session.get(Rule.class, id);
            if (rule == null) {
                logger.error("Unable to find the rule {} to be edited.", id);
                throw new MappingHttp.NetshotBadRequestException("Unable to find this rule.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_RULE);
            }

            if (rsRule.getName() != null) {
                String name = rsRule.getName().trim();
                if (name.isEmpty()) {
                    logger.warn("User posted an empty rule name.");
                    throw new MappingHttp.NetshotBadRequestException("Invalid rule name.",
                            MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_RULE_NAME);
                }
                rule.setName(name);
            }
            rule.setEnabled(rsRule.isEnabled());

            Map<Long, Date> postedExemptions = new HashMap<Long, Date>();
            postedExemptions.putAll(rsRule.getExemptions());
            Iterator<Exemption> i = rule.getExemptions().iterator();
            while (i.hasNext()) {
                Exemption exemption = i.next();
                Long deviceId = exemption.getDevice().getId();
                if (postedExemptions.containsKey(deviceId)) {
                    exemption.setExpirationDate(postedExemptions.get(deviceId));
                    postedExemptions.remove(deviceId);
                } else {
                    i.remove();
                }
            }
            for (Map.Entry<Long, Date> postedExemption : postedExemptions.entrySet()) {
                Device device = (Device) session.load(Device.class, postedExemption.getKey());
                Exemption exemption = new Exemption(rule, device, postedExemption.getValue());
                rule.addExemption(exemption);
            }

            if (rule instanceof JavaScriptRule) {
                if (rsRule.getScript() != null) {
                    String script = rsRule.getScript().trim();
                    ((JavaScriptRule) rule).setScript(script);
                }
            } else if (rule instanceof TextRule) {
                if (rsRule.getText() != null) {
                    ((TextRule) rule).setText(rsRule.getText());
                }
                if (rsRule.isRegExp() != null) {
                    ((TextRule) rule).setRegExp(rsRule.isRegExp());
                }
                if (rsRule.getContext() != null) {
                    ((TextRule) rule).setContext(rsRule.getContext());
                }
                if (rsRule.getField() != null) {
                    ((TextRule) rule).setField(rsRule.getField());
                }
                if (rsRule.getDriver() != null) {
                    ((TextRule) rule).setDeviceDriver(rsRule.getDriver());
                }
                if (rsRule.isInvert() != null) {
                    ((TextRule) rule).setInvert(rsRule.isInvert());
                }
                if (rsRule.isMatchAll() != null) {
                    ((TextRule) rule).setMatchAll(rsRule.isMatchAll());
                }
                if (rsRule.isAnyBlock() != null) {
                    ((TextRule) rule).setAnyBlock(rsRule.isAnyBlock());
                }
            }

            session.update(rule);
            session.getTransaction().commit();
            return rule;
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Error while saving the new rule.", e);
            Throwable t = e.getCause();
            if (t != null && t.getMessage().contains("Duplicate entry")) {
                throw new MappingHttp.NetshotBadRequestException(
                        "A rule with this name already exists.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DUPLICATE_RULE);
            }
            throw new MappingHttp.NetshotBadRequestException(
                    "Unable to save the rule.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } catch (WebApplicationException e) {
            session.getTransaction().rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    /**
     * Delete rule.
     *
     * @param id the id
     * @throws WebApplicationException the web application exception
     */
    @DELETE
    @Path("rules/{id}")
    @RolesAllowed("readwrite")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public void deleteRule(@PathParam("id") Long id)
            throws WebApplicationException {
        logger.debug("REST request, delete rule {}.", id);
        Session session = Database.getSession();
        try {
            session.beginTransaction();
            Rule rule = (Rule) session.load(Rule.class, id);
            session.delete(rule);
            session.getTransaction().commit();
        } catch (ObjectNotFoundException e) {
            session.getTransaction().rollback();
            logger.error("The rule {} to be deleted doesn't exist.", id, e);
            throw new MappingHttp.NetshotBadRequestException("The rule doesn't exist.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_RULE);
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Unable to delete the rule {}.", id, e);
            throw new MappingHttp.NetshotBadRequestException("Unable to delete the rule.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Test js rule.
     *
     * @param rsRule the rs rule
     * @return the rs js rule test result
     * @throws WebApplicationException the web application exception
     */
    @POST
    @Path("rules/test")
    @RolesAllowed("readonly")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public MappingHttp.RsRuleTestResult testRule(MappingHttp.RsRuleTest rsRule) throws WebApplicationException {
        logger.debug("REST request, rule test.");
        Device device;
        Session session = Database.getSession();
        try {
            device = (Device) session
                    .createQuery("from Device d join fetch d.lastConfig where d.id = :id")
                    .setLong("id", rsRule.getDevice()).uniqueResult();
            if (device == null) {
                logger.warn("Unable to find the device {}.", rsRule.getDevice());
                throw new MappingHttp.NetshotBadRequestException("Unable to find the device.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DEVICE);
            }

            Rule rule;

            if (".TextRule".equals(rsRule.getType())) {
                TextRule txRule = new TextRule("TEST", null);
                txRule.setDeviceDriver(rsRule.getDriver());
                txRule.setField(rsRule.getField());
                txRule.setInvert(rsRule.isInvert());
                txRule.setContext(rsRule.getContext());
                txRule.setRegExp(rsRule.isRegExp());
                txRule.setText(rsRule.getText());
                txRule.setAnyBlock(rsRule.isAnyBlock());
                txRule.setMatchAll(rsRule.isMatchAll());
                rule = txRule;
            } else {
                JavaScriptRule jsRule = new JavaScriptRule("TEST", null);
                jsRule.setScript(rsRule.getScript());
                rule = jsRule;
            }

            MappingHttp.RsRuleTestResult result = new MappingHttp.RsRuleTestResult();

            rule.setEnabled(true);
            rule.check(device, session);
            result.setResult(rule.getCheckResults().iterator().next().getResult());
            result.setScriptError(rule.getPlainLog());

            return result;
        } catch (Exception e) {
            logger.error("Unable to retrieve the device {}.", rsRule.getDevice(), e);
            throw new MappingHttp.NetshotBadRequestException("Unable to retrieve the device.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Gets the exempted devices.
     *
     * @param id the id
     * @return the exempted devices
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("devices/rule/{id}")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<MappingHttp.RsLightExemptedDevice> getExemptedDevices(@PathParam("id") Long id) throws WebApplicationException {
        logger.debug("REST request, get exemptions for rule {}.", id);
        Session session = Database.getSession();
        try {
            @SuppressWarnings("unchecked")
            List<MappingHttp.RsLightExemptedDevice> exemptions = session
                    .createQuery(DEVICELIST_BASEQUERY + ", e.expirationDate as expirationDate from Exemption e join e.key.device d where e.key.rule.id = :id")
                    .setLong("id", id)
                    .setResultTransformer(Transformers.aliasToBean(MappingHttp.RsLightExemptedDevice.class))
                    .list();
            return exemptions;
        } catch (HibernateException e) {
            logger.error("Unable to fetch the exemptions.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the exemptions",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Gets the device compliance.
     *
     * @param id the id
     * @return the device compliance
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("rules/device/{id}")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<MappingHttp.RsDeviceRule> getDeviceCompliance(@PathParam("id") Long id) throws WebApplicationException {
        logger.debug("REST request, get exemptions for rules {}.", id);
        Session session = Database.getSession();
        try {
            @SuppressWarnings("unchecked")
            List<MappingHttp.RsDeviceRule> rules = session.createQuery("select r.id as id, r.name as ruleName, p.name as policyName, cr.result as result, cr.checkDate as checkDate, cr.comment as comment, e.expirationDate as expirationDate from Rule r join r.policy p join p.targetGroup g join g.cachedDevices d1 with d1.id = :id left join r.checkResults cr with cr.key.device.id = :id left join r.exemptions e with e.key.device.id = :id")
                    .setLong("id", id)
                    .setResultTransformer(Transformers.aliasToBean(MappingHttp.RsDeviceRule.class))
                    .list();
            return rules;
        } catch (HibernateException e) {
            logger.error("Unable to fetch the rules.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the rules",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Gets the last7 days changes by day stats.
     *
     * @return the last7 days changes by day stats
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("reports/last7dayschangesbyday")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<MappingHttp.RsConfigChangeNumberByDateStat> getLast7DaysChangesByDayStats() throws WebApplicationException {
        logger.debug("REST request, get last 7 day changes by day stats.");
        Session session = Database.getSession();
        try {
            @SuppressWarnings("unchecked")
            List<MappingHttp.RsConfigChangeNumberByDateStat> stats = session
                    .createQuery("select count(c) as changeCount, cast(cast(c.changeDate as date) as timestamp) as changeDay from Config c group by cast(c.changeDate as date) order by changeDate desc")
                    .setMaxResults(7)
                    .setResultTransformer(Transformers.aliasToBean(MappingHttp.RsConfigChangeNumberByDateStat.class))
                    .list();
            return stats;
        } catch (HibernateException e) {
            logger.error("Unable to get the stats.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to get the stats",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Gets the group config compliance stats.
     *
     * @return the group config compliance stats
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("reports/groupconfigcompliancestats")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<MappingHttp.RsGroupConfigComplianceStat> getGroupConfigComplianceStats() throws WebApplicationException {
        logger.debug("REST request, group config compliance stats.");
        Session session = Database.getSession();
        try {
            @SuppressWarnings("unchecked")
            List<MappingHttp.RsGroupConfigComplianceStat> stats = session
                    .createQuery("select g.id as groupId, g.name as groupName, (select count(d) from g.cachedDevices d where d.status = :enabled and (select count(ccr.result) from d.complianceCheckResults ccr where ccr.result = :nonConforming) = 0) as compliantDeviceCount, (select count(d) from g.cachedDevices d where d.status = :enabled) as deviceCount from DeviceGroup g where g.hiddenFromReports <> true")
                    .setParameter("nonConforming", CheckResult.ResultOption.NONCONFORMING)
                    .setParameter("enabled", Device.Status.INPRODUCTION)
                    .setResultTransformer(Transformers.aliasToBean(MappingHttp.RsGroupConfigComplianceStat.class))
                    .list();
            return stats;
        } catch (HibernateException e) {
            logger.error("Unable to get the stats.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to get the stats",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    @GET
    @Path("reports/hardwaresupportstats")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<MappingHttp.RsHardwareSupportStat> getHardwareSupportStats() throws WebApplicationException {
        logger.debug("REST request, hardware support stats.");
        Session session = Database.getSession();
        try {
            @SuppressWarnings("unchecked")
            List<MappingHttp.RsHardwareSupportStat> eosStats = session
                    .createQuery("select count(d) as deviceCount, d.eosDate AS eoxDate from Device d where d.status = :enabled group by d.eosDate")
                    .setParameter("enabled", Device.Status.INPRODUCTION)
                    .setResultTransformer(Transformers.aliasToBean(MappingHttp.RsHardwareSupportEoSStat.class))
                    .list();
            @SuppressWarnings("unchecked")
            List<MappingHttp.RsHardwareSupportStat> eolStats = session
                    .createQuery("select count(d) as deviceCount, d.eolDate AS eoxDate from Device d where d.status = :enabled group by d.eolDate")
                    .setParameter("enabled", Device.Status.INPRODUCTION)
                    .setResultTransformer(Transformers.aliasToBean(MappingHttp.RsHardwareSupportEoLStat.class))
                    .list();
            List<MappingHttp.RsHardwareSupportStat> stats = new ArrayList<MappingHttp.RsHardwareSupportStat>();
            stats.addAll(eosStats);
            stats.addAll(eolStats);
            return stats;
        } catch (HibernateException e) {
            logger.error("Unable to ge"
                    + ""
                    + "t the stats.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to get the stats",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Gets the group software compliance stats.
     *
     * @return the group software compliance stats
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("reports/groupsoftwarecompliancestats")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<MappingHttp.RsGroupSoftwareComplianceStat> getGroupSoftwareComplianceStats() throws WebApplicationException {
        logger.debug("REST request, group software compliance stats.");
        Session session = Database.getSession();
        try {
            @SuppressWarnings("unchecked")
            List<MappingHttp.RsGroupSoftwareComplianceStat> stats = session
                    .createQuery("select g.id as groupId, g.name as groupName, (select count(d) from g.cachedDevices d where d.status = :enabled and d.softwareLevel = :gold) as goldDeviceCount, (select count(d) from g.cachedDevices d where d.status = :enabled and d.softwareLevel = :silver) as silverDeviceCount, (select count(d) from g.cachedDevices d where d.status = :enabled and d.softwareLevel = :bronze) as bronzeDeviceCount, (select count(d) from g.cachedDevices d where d.status = :enabled) as deviceCount from DeviceGroup g where g.hiddenFromReports <> true")
                    .setParameter("gold", ConformanceLevel.GOLD)
                    .setParameter("silver", ConformanceLevel.SILVER)
                    .setParameter("bronze", ConformanceLevel.BRONZE)
                    .setParameter("enabled", Device.Status.INPRODUCTION)
                    .setResultTransformer(Transformers.aliasToBean(MappingHttp.RsGroupSoftwareComplianceStat.class))
                    .list();
            return stats;
        } catch (HibernateException e) {
            logger.error("Unable to get the stats.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to get the stats",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Gets the group config non compliant devices.
     *
     * @param id the id
     * @return the group config non compliant devices
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("reports/groupconfignoncompliantdevices/{id}")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<MappingHttp.RsLightPolicyRuleDevice> getGroupConfigNonCompliantDevices(@PathParam("id") Long id) throws WebApplicationException {
        logger.debug("REST request, group config non compliant devices.");
        Session session = Database.getSession();
        try {
            @SuppressWarnings("unchecked")
            List<MappingHttp.RsLightPolicyRuleDevice> devices = session
                    .createQuery(DEVICELIST_BASEQUERY + ", p.name as policyName, r.name as ruleName, ccr.checkDate as checkDate, ccr.result as result from Device d join d.ownerGroups g join d.complianceCheckResults ccr join ccr.key.rule r join r.policy p where g.id = :id and ccr.result = :nonConforming and d.status = :enabled")
                    .setLong("id", id)
                    .setParameter("nonConforming", CheckResult.ResultOption.NONCONFORMING)
                    .setParameter("enabled", Device.Status.INPRODUCTION)
                    .setResultTransformer(Transformers.aliasToBean(MappingHttp.RsLightPolicyRuleDevice.class))
                    .list();
            return devices;
        } catch (HibernateException e) {
            logger.error("Unable to get the devices.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to get the stats",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    @GET
    @Path("reports/hardwaresupportdevices/{type}/{date}")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<MappingHttp.RsLightDevice> getHardwareStatusDevices(@PathParam("type") String type, @PathParam("date") Long date) throws WebApplicationException {
        logger.debug("REST request, EoX devices by type and date.");
        if (!type.equals("eol") && !type.equals("eos")) {
            logger.error("Invalid requested EoX type.");
            throw new MappingHttp.NetshotBadRequestException("Unable to get the stats",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        }
        Date eoxDate = new Date(date);
        Session session = Database.getSession();
        try {
            if (date == 0) {
                @SuppressWarnings("unchecked")
                List<MappingHttp.RsLightDevice> devices = session
                        .createQuery(DEVICELIST_BASEQUERY + "from Device d where d." + type + "Date is null and d.status = :enabled")
                        .setParameter("enabled", Device.Status.INPRODUCTION)
                        .setResultTransformer(Transformers.aliasToBean(MappingHttp.RsLightDevice.class))
                        .list();
                return devices;
            } else {
                @SuppressWarnings("unchecked")
                List<MappingHttp.RsLightDevice> devices = session
                        .createQuery(DEVICELIST_BASEQUERY + "from Device d where date(d." + type + "Date) = :eoxDate and d.status = :enabled")
                        .setDate("eoxDate", eoxDate)
                        .setParameter("enabled", Device.Status.INPRODUCTION)
                        .setResultTransformer(Transformers.aliasToBean(MappingHttp.RsLightDevice.class))
                        .list();
                return devices;
            }
        } catch (HibernateException e) {
            logger.error("Unable to get the devices.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to get the stats",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Gets the hardware rules.
     *
     * @return the harware rules
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("hardwarerules")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<HardwareRule> getHardwareRules() throws WebApplicationException {
        logger.debug("REST request, hardware rules.");
        Session session = Database.getSession();
        try {
            @SuppressWarnings("unchecked")
            List<HardwareRule> rules = session
                    .createQuery("from HardwareRule r left join fetch r.targetGroup g")
                    .list();
            return rules;
        } catch (HibernateException e) {
            logger.error("Unable to fetch the hardware rules.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the hardware rules.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Adds an hardware rule.
     *
     * @param rsRule the rs rule
     * @return the hardware rule
     * @throws WebApplicationException the web application exception
     */
    @POST
    @Path("hardwarerules")
    @RolesAllowed("readwrite")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public HardwareRule addHardwareRule(MappingHttp.RsHardwareRule rsRule) throws WebApplicationException {
        logger.debug("REST request, add hardware rule.");

        HardwareRule rule;
        Session session = Database.getSession();
        try {
            session.beginTransaction();

            DeviceGroup group = null;
            if (rsRule.getGroup() != -1) {
                group = (DeviceGroup) session.load(DeviceGroup.class, rsRule.getGroup());
            }

            String driver = rsRule.getDriver();
            if (DeviceDriver.getDriverByName(driver) == null) {
                driver = null;
            }

            rule = new HardwareRule(driver, group,
                    rsRule.getFamily(), rsRule.isFamilyRegExp(), rsRule.getPartNumber(),
                    rsRule.isPartNumberRegExp(), rsRule.getEndOfSale(), rsRule.getEndOfLife());

            session.save(rule);
            session.getTransaction().commit();
        } catch (ObjectNotFoundException e) {
            session.getTransaction().rollback();
            logger.error("The posted group doesn't exist", e);
            throw new MappingHttp.NetshotBadRequestException(
                    "Invalid group",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_GROUP);
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Error while saving the new rule.", e);
            throw new MappingHttp.NetshotBadRequestException(
                    "Unable to add the rule to the database",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
        return rule;
    }

    /**
     * Delete software rule.
     *
     * @param id the id
     * @throws WebApplicationException the web application exception
     */
    @DELETE
    @Path("hardwarerules/{id}")
    @RolesAllowed("readwrite")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public void deleteHardwareRule(@PathParam("id") Long id)
            throws WebApplicationException {
        logger.debug("REST request, delete hardware rule {}.", id);
        Session session = Database.getSession();
        try {
            session.beginTransaction();
            HardwareRule rule = (HardwareRule) session.load(HardwareRule.class, id);
            session.delete(rule);
            session.getTransaction().commit();
        } catch (ObjectNotFoundException e) {
            session.getTransaction().rollback();
            logger.error("The rule {} to be deleted doesn't exist.", id, e);
            throw new MappingHttp.NetshotBadRequestException("The rule doesn't exist.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_RULE);
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Unable to delete the rule {}.", id, e);
            throw new MappingHttp.NetshotBadRequestException("Unable to delete the rule.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Sets the hardware rule.
     *
     * @param id     the id
     * @param rsRule the rs rule
     * @return the hardware rule
     * @throws WebApplicationException the web application exception
     */
    @PUT
    @Path("hardwarerules/{id}")
    @RolesAllowed("readwrite")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public HardwareRule setHardwareRule(@PathParam("id") Long id, MappingHttp.RsHardwareRule rsRule)
            throws WebApplicationException {
        logger.debug("REST request, edit hardware rule {}.", id);
        Session session = Database.getSession();
        try {
            session.beginTransaction();
            HardwareRule rule = (HardwareRule) session.get(HardwareRule.class, id);
            if (rule == null) {
                logger.error("Unable to find the rule {} to be edited.", id);
                throw new MappingHttp.NetshotBadRequestException("Unable to find this rule.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_RULE);
            }

            String driver = rsRule.getDriver();
            if (DeviceDriver.getDriverByName(driver) == null) {
                driver = null;
            }
            rule.setDriver(driver);

            DeviceGroup group = null;
            if (rsRule.getGroup() != -1) {
                group = (DeviceGroup) session.load(DeviceGroup.class, rsRule.getGroup());
            }
            rule.setTargetGroup(group);

            rule.setFamily(rsRule.getFamily());
            rule.setFamilyRegExp(rsRule.isFamilyRegExp());
            rule.setEndOfLife(rsRule.getEndOfLife());
            rule.setEndOfSale(rsRule.getEndOfSale());
            rule.setPartNumber(rsRule.getPartNumber());
            rule.setPartNumberRegExp(rsRule.isPartNumberRegExp());

            session.update(rule);
            session.getTransaction().commit();
            return rule;
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Error while saving the rule.", e);
            throw new MappingHttp.NetshotBadRequestException(
                    "Unable to save the rule.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } catch (WebApplicationException e) {
            session.getTransaction().rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    /**
     * Gets the software rules.
     *
     * @return the software rules
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("softwarerules")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<SoftwareRule> getSoftwareRules() throws WebApplicationException {
        logger.debug("REST request, software rules.");
        Session session = Database.getSession();
        try {
            @SuppressWarnings("unchecked")
            List<SoftwareRule> rules = session
                    .createQuery("from SoftwareRule r left join fetch r.targetGroup g")
                    .list();
            return rules;
        } catch (HibernateException e) {
            logger.error("Unable to fetch the software rules.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the software rules.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Adds the software rule.
     *
     * @param rsRule the rs rule
     * @return the software rule
     * @throws WebApplicationException the web application exception
     */
    @POST
    @Path("softwarerules")
    @RolesAllowed("readwrite")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public SoftwareRule addSoftwareRule(MappingHttp.RsSoftwareRule rsRule) throws WebApplicationException {
        logger.debug("REST request, add software rule.");

        SoftwareRule rule;
        Session session = Database.getSession();
        try {
            session.beginTransaction();

            DeviceGroup group = null;
            if (rsRule.getGroup() != -1) {
                group = (DeviceGroup) session.load(DeviceGroup.class, rsRule.getGroup());
            }

            String driver = rsRule.getDriver();
            if (DeviceDriver.getDriverByName(driver) == null) {
                driver = null;
            }

            rule = new SoftwareRule(rsRule.getPriority(), group, driver,
                    rsRule.getFamily(), rsRule.isFamilyRegExp(), rsRule.getVersion(),
                    rsRule.isVersionRegExp(), rsRule.getLevel());

            session.save(rule);
            session.getTransaction().commit();
        } catch (ObjectNotFoundException e) {
            session.getTransaction().rollback();
            logger.error("The posted group doesn't exist", e);
            throw new MappingHttp.NetshotBadRequestException(
                    "Invalid group",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_GROUP);
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Error while saving the new rule.", e);
            throw new MappingHttp.NetshotBadRequestException(
                    "Unable to add the policy to the database",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
        return rule;
    }

    /**
     * Delete software rule.
     *
     * @param id the id
     * @throws WebApplicationException the web application exception
     */
    @DELETE
    @Path("softwarerules/{id}")
    @RolesAllowed("readwrite")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public void deleteSoftwareRule(@PathParam("id") Long id)
            throws WebApplicationException {
        logger.debug("REST request, delete software rule {}.", id);
        Session session = Database.getSession();
        try {
            session.beginTransaction();
            SoftwareRule rule = (SoftwareRule) session.load(SoftwareRule.class, id);
            session.delete(rule);
            session.getTransaction().commit();
        } catch (ObjectNotFoundException e) {
            session.getTransaction().rollback();
            logger.error("The rule {} to be deleted doesn't exist.", id, e);
            throw new MappingHttp.NetshotBadRequestException("The rule doesn't exist.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_RULE);
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Unable to delete the rule {}.", id, e);
            throw new MappingHttp.NetshotBadRequestException("Unable to delete the rule.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Sets the software rule.
     *
     * @param id     the id
     * @param rsRule the rs rule
     * @return the software rule
     * @throws WebApplicationException the web application exception
     */
    @PUT
    @Path("softwarerules/{id}")
    @RolesAllowed("readwrite")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public SoftwareRule setSoftwareRule(@PathParam("id") Long id, MappingHttp.RsSoftwareRule rsRule)
            throws WebApplicationException {
        logger.debug("REST request, edit software rule {}.", id);
        Session session = Database.getSession();
        try {
            session.beginTransaction();
            SoftwareRule rule = (SoftwareRule) session.get(SoftwareRule.class, id);
            if (rule == null) {
                logger.error("Unable to find the rule {} to be edited.", id);
                throw new MappingHttp.NetshotBadRequestException("Unable to find this rule.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_RULE);
            }

            String driver = rsRule.getDriver();
            if (DeviceDriver.getDriverByName(driver) == null) {
                driver = null;
            }
            rule.setDriver(driver);

            DeviceGroup group = null;
            if (rsRule.getGroup() != -1) {
                group = (DeviceGroup) session.load(DeviceGroup.class, rsRule.getGroup());
            }
            rule.setTargetGroup(group);

            rule.setFamily(rsRule.getFamily());
            rule.setFamilyRegExp(rsRule.isFamilyRegExp());
            rule.setVersion(rsRule.getVersion());
            rule.setVersionRegExp(rsRule.isVersionRegExp());
            rule.setPriority(rsRule.getPriority());
            rule.setLevel(rsRule.getLevel());

            session.update(rule);
            session.getTransaction().commit();
            return rule;
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Error while saving the rule.", e);
            throw new MappingHttp.NetshotBadRequestException(
                    "Unable to save the rule.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } catch (WebApplicationException e) {
            session.getTransaction().rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    /**
     * Gets the group devices by software level.
     *
     * @param id    the id
     * @param level the level
     * @return the group devices by software level
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("reports/groupdevicesbysoftwarelevel/{id}/{level}")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<MappingHttp.RsLightSoftwareLevelDevice> getGroupDevicesBySoftwareLevel(@PathParam("id") Long id, @PathParam("level") String level) throws WebApplicationException {
        logger.debug("REST request, group {} devices by software level {}.", id, level);
        Session session = Database.getSession();

        ConformanceLevel filterLevel = ConformanceLevel.UNKNOWN;
        for (ConformanceLevel l : ConformanceLevel.values()) {
            if (l.toString().equalsIgnoreCase(level)) {
                filterLevel = l;
                break;
            }
        }

        try {
            @SuppressWarnings("unchecked")
            List<MappingHttp.RsLightSoftwareLevelDevice> devices = session
                    .createQuery(DEVICELIST_BASEQUERY + ", d.softwareLevel as softwareLevel from Device d join d.ownerGroups g where g.id = :id and d.softwareLevel = :level and d.status = :enabled")
                    .setLong("id", id)
                    .setParameter("level", filterLevel)
                    .setParameter("enabled", Device.Status.INPRODUCTION)
                    .setResultTransformer(Transformers.aliasToBean(MappingHttp.RsLightSoftwareLevelDevice.class))
                    .list();
            return devices;
        } catch (HibernateException e) {
            logger.error("Unable to get the devices.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to get the stats",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    @GET
    @Path("reports/accessfailuredevices/{days}")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<MappingHttp.RsLightAccessFailureDevice> getAccessFailureDevices(@PathParam("days") Integer days) throws WebApplicationException {
        logger.debug("REST request, devices without successful snapshot over the last {} days.", days);

        if (days == null || days < 1) {
            logger.warn("Invalid number of days {} to find the unreachable devices, using 3.", days);
            days = 3;
        }

        Session session = Database.getSession();

        try {
            Calendar when = Calendar.getInstance();
            when.add(Calendar.DATE, -days);

            @SuppressWarnings("unchecked")
            List<MappingHttp.RsLightAccessFailureDevice> devices = session
                    .createQuery(DEVICELIST_BASEQUERY + ", (select max(t.executionDate) from TakeSnapshotTask t where t.device = d and t.status = :success) as lastSuccess, (select max(t.executionDate) from TakeSnapshotTask t where t.device = d and t.status = :failure) as lastFailure from Device d where d.status = :enabled")
                    .setParameter("success", Task.Status.SUCCESS)
                    .setParameter("failure", Task.Status.FAILURE)
                    .setParameter("enabled", Device.Status.INPRODUCTION)
                    .setResultTransformer(Transformers.aliasToBean(MappingHttp.RsLightAccessFailureDevice.class))
                    .list();
            Iterator<MappingHttp.RsLightAccessFailureDevice> d = devices.iterator();
            while (d.hasNext()) {
                MappingHttp.RsLightAccessFailureDevice device = d.next();
                if (device.getLastSuccess() != null && device.getLastSuccess().after(when.getTime())) {
                    d.remove();
                }
            }
            return devices;
        } catch (HibernateException e) {
            logger.error("Unable to get the devices.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to get the stats",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Logout.
     *
     * @return the boolean
     * @throws WebApplicationException the web application exception
     */
    @DELETE
    @Path("user/{id}")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public void logout(@Context HttpServletRequest request) throws WebApplicationException {
        logger.debug("REST logout request.");
        HttpSession httpSession = request.getSession();
        httpSession.invalidate();
    }

    /**
     * Sets the password.
     *
     * @param rsLogin the rs login
     * @return the user
     * @throws WebApplicationException the web application exception
     */
    @PUT
    @Path("user/{id}")
    @RolesAllowed("readonly")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public User setPassword(@Context HttpServletRequest request, MappingHttp.RsLogin rsLogin) throws WebApplicationException {
        logger.debug("REST password change request, username {}.", rsLogin.getUsername());
        User sessionUser = (User) request.getSession().getAttribute("user");

        User user;
        Session session = Database.getSession();
        try {
            session.beginTransaction();
            user = (User) session.bySimpleNaturalId(User.class).load(rsLogin.getUsername());
            if (user == null || !user.getUsername().equals(sessionUser.getUsername()) || !user.isLocal()) {
                throw new MappingHttp.NetshotBadRequestException("Invalid user.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_USER);
            }

            if (!user.checkPassword(rsLogin.getPassword())) {
                throw new MappingHttp.NetshotBadRequestException("Invalid current password.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_USER);
            }

            String newPassword = rsLogin.getNewPassword();
            if (newPassword.equals("")) {
                throw new MappingHttp.NetshotBadRequestException("The password cannot be empty.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_USER);
            }

            user.setPassword(newPassword);
            session.save(user);
            session.getTransaction().commit();
            return sessionUser;
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Unable to retrieve the user {}.", rsLogin.getUsername(), e);
            throw new MappingHttp.NetshotBadRequestException("Unable to retrieve the user.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Login.
     *
     * @param rsLogin the rs login
     * @return the user
     * @throws WebApplicationException the web application exception
     */
    @POST
    @PermitAll
    @Path("user")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public User login(@Context HttpServletRequest request, MappingHttp.RsLogin rsLogin) throws WebApplicationException {
        logger.debug("REST authentication request, username {}.", rsLogin.getUsername());

        User user = null;

        Session session = Database.getSession();
        try {
            user = (User) session.bySimpleNaturalId(User.class).load(rsLogin.getUsername());
        } catch (HibernateException e) {
            logger.error("Unable to retrieve the user {}.", rsLogin.getUsername(), e);
            throw new MappingHttp.NetshotBadRequestException("Unable to retrieve the user.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }

        if (user != null && user.isLocal()) {
            if (!user.checkPassword(rsLogin.getPassword())) {
                user = null;
            }
        } else {
            User remoteUser = Radius.authenticate(rsLogin.getUsername(), rsLogin.getPassword());
            if (remoteUser != null && user != null) {
                remoteUser.setLevel(user.getLevel());
            }
            user = remoteUser;
        }
        if (user == null) {
            HttpSession httpSession = request.getSession();
            httpSession.invalidate();
        } else {
            HttpSession httpSession = request.getSession();
            httpSession.setAttribute("user", user);
            httpSession.setMaxInactiveInterval(User.MAX_IDLE_TIME);
            return user;
        }
        throw new WebApplicationException(Response.status(Response.Status.UNAUTHORIZED).build());
    }

    /**
     * Gets the user.
     *
     * @return the user
     * @throws WebApplicationException the web application exception
     */
    @GET
    @RolesAllowed("readonly")
    @Path("user")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public User getUser(@Context HttpServletRequest request) throws WebApplicationException {
        User user = (User) request.getSession().getAttribute("user");
        return user;
    }

    /**
     * Gets the users.
     *
     * @return the users
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("/users")
    @RolesAllowed("admin")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<User> getUsers() throws WebApplicationException {
        logger.debug("REST request, get user list.");
        Session session = Database.getSession();
        try {
            @SuppressWarnings("unchecked")
            List<User> users = session.createCriteria(User.class).list();
            return users;
        } catch (HibernateException e) {
            logger.error("Unable to retrieve the users.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to retrieve the users.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Adds the user.
     *
     * @param rsUser the rs user
     * @return the user
     */
    @POST
    @Path("users")
    @RolesAllowed("admin")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public User addUser(MappingHttp.RsUser rsUser) {
        logger.debug("REST request, add user");

        String username = rsUser.getUsername();
        if (username == null || username.trim().isEmpty()) {
            logger.warn("User posted an empty user name.");
            throw new MappingHttp.NetshotBadRequestException("Invalid user name.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_USER_NAME);
        }
        username = username.trim();

        String password = rsUser.getPassword();
        if (rsUser.isLocal()) {
            if (password == null || password.equals("")) {
                logger.warn("User tries to create a local account without password.");
                throw new MappingHttp.NetshotBadRequestException("Please set a password.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_PASSWORD);
            }
        } else {
            password = "";
        }

        User user = new User(username, rsUser.isLocal(), password);
        user.setLevel(rsUser.getLevel());

        Session session = Database.getSession();
        try {
            session.beginTransaction();
            session.save(user);
            session.getTransaction().commit();
            return user;
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Error while saving the new user.", e);
            Throwable t = e.getCause();
            if (t != null && t.getMessage().contains("Duplicate entry")) {
                throw new MappingHttp.NetshotBadRequestException(
                        "A user with this name already exists.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DUPLICATE_USER);
            }
            throw new MappingHttp.NetshotBadRequestException(
                    "Unable to add the group to the database",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }

    }

    /**
     * Sets the user.
     *
     * @param id     the id
     * @param rsUser the rs user
     * @return the user
     * @throws WebApplicationException the web application exception
     */
    @PUT
    @Path("users/{id}")
    @RolesAllowed("admin")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public User setUser(@PathParam("id") Long id, MappingHttp.RsUser rsUser)
            throws WebApplicationException {
        logger.debug("REST request, edit user {}.", id);
        Session session = Database.getSession();
        try {
            session.beginTransaction();
            User user = (User) session.get(User.class, id);
            if (user == null) {
                logger.error("Unable to find the user {} to be edited.", id);
                throw new MappingHttp.NetshotBadRequestException("Unable to find this user.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_USER);
            }

            String username = rsUser.getUsername();
            if (username == null || username.trim().isEmpty()) {
                logger.warn("User posted an empty user name.");
                throw new MappingHttp.NetshotBadRequestException("Invalid user name.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_USER_NAME);
            }
            username = username.trim();
            user.setUsername(username);

            user.setLevel(rsUser.getLevel());
            if (rsUser.isLocal()) {
                if (rsUser.getPassword() != null && !rsUser.getPassword().equals("-")) {
                    user.setPassword(rsUser.getPassword());
                }
                if (user.getHashedPassword().equals("")) {
                    logger.error("The password cannot be empty for user {}.", id);
                    throw new MappingHttp.NetshotBadRequestException("You must set a password.",
                            MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_PASSWORD);
                }
            } else {
                user.setPassword("");
            }
            user.setLocal(rsUser.isLocal());
            session.update(user);
            session.getTransaction().commit();
            return user;
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Unable to save the user {}.", id, e);
            Throwable t = e.getCause();
            if (t != null && t.getMessage().contains("Duplicate entry")) {
                throw new MappingHttp.NetshotBadRequestException(
                        "A user with this name already exists.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DUPLICATE_USER);
            }
            throw new MappingHttp.NetshotBadRequestException("Unable to save the user.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } catch (WebApplicationException e) {
            session.getTransaction().rollback();
            throw e;
        } finally {
            session.close();
        }
    }

    /**
     * Delete user.
     *
     * @param id the id
     * @throws WebApplicationException the web application exception
     */
    @DELETE
    @Path("users/{id}")
    @RolesAllowed("admin")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public void deleteUser(@PathParam("id") Long id)
            throws WebApplicationException {
        logger.debug("REST request, delete user {}.", id);
        Session session = Database.getSession();
        try {
            session.beginTransaction();
            User user = (User) session.load(User.class, id);
            session.delete(user);
            session.getTransaction().commit();
        } catch (ObjectNotFoundException e) {
            session.getTransaction().rollback();
            logger.error("The user doesn't exist.");
            throw new MappingHttp.NetshotBadRequestException("The user doesn't exist.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_USER);
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            throw new MappingHttp.NetshotBadRequestException("Unable to delete the user.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    @GET
    @Path("reports/export")
    @RolesAllowed("readonly")
    @Produces({"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"})
    public Response getDataXLSX(@Context HttpServletRequest request,
                                @DefaultValue("-1") @QueryParam("group") long group,
                                @DefaultValue("false") @QueryParam("interfaces") boolean exportInterfaces,
                                @DefaultValue("false") @QueryParam("inventory") boolean exportInventory,
                                @DefaultValue("xlsx") @QueryParam("format") String fileFormat) throws WebApplicationException {
        logger.debug("REST request, export data.");
        User user = (User) request.getSession().getAttribute("user");

        if (fileFormat.compareToIgnoreCase("xlsx") == 0) {
            String fileName = String.format("netshot-export_%s.xlsx", (new SimpleDateFormat("yyyyMMdd-HHmmss")).format(new Date()));

            Session session = Database.getSession();
            try {
                Workbook workBook = new XSSFWorkbook();
                Row row;
                Cell cell;

                CreationHelper createHelper = workBook.getCreationHelper();
                CellStyle datetimeCellStyle = workBook.createCellStyle();
                datetimeCellStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
                CellStyle dateCellStyle = workBook.createCellStyle();
                dateCellStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-mm-dd"));

                Sheet summarySheet = workBook.createSheet("Summary");
                row = summarySheet.createRow(0);
                row.createCell(0).setCellValue("Netshot version");
                row.createCell(1).setCellValue(Netshot.VERSION);
                row = summarySheet.createRow(1);
                row.createCell(0).setCellValue("Exported by");
                row.createCell(1).setCellValue(user.getName());
                row = summarySheet.createRow(2);
                row.createCell(0).setCellValue("Date and time");
                cell = row.createCell(1);
                cell.setCellValue(new Date());
                cell.setCellStyle(datetimeCellStyle);
                row = summarySheet.createRow(4);
                row.createCell(0).setCellValue("Selected Group");
                Query query;
                if (group == -1) {
                    query = session.createQuery("select d from Device d");
                    row.createCell(1).setCellValue("None");
                } else {
                    query = session
                            .createQuery("select d from Device d join d.ownerGroups g where g.id = :id")
                            .setLong("id", group);
                    DeviceGroup deviceGroup = (DeviceGroup) session.get(DeviceGroup.class, group);
                    row.createCell(1).setCellValue(deviceGroup.getName());
                }

                Sheet deviceSheet = workBook.createSheet("Devices");
                row = deviceSheet.createRow(0);
                row.createCell(0).setCellValue("ID");
                row.createCell(1).setCellValue("Name");
                row.createCell(2).setCellValue("Management IP");
                row.createCell(3).setCellValue("Domain");
                row.createCell(4).setCellValue("Network Class");
                row.createCell(5).setCellValue("Family");
                row.createCell(6).setCellValue("Creation");
                row.createCell(7).setCellValue("Last Change");
                row.createCell(8).setCellValue("Software");
                row.createCell(9).setCellValue("End of Sale Date");
                row.createCell(10).setCellValue("End Of Life Date");

                int yDevice = 1;

                @SuppressWarnings("unchecked")
                List<Device> devices = query.list();
                for (Device device : devices) {
                    row = deviceSheet.createRow(yDevice++);
                    row.createCell(0).setCellValue(device.getId());
                    row.createCell(1).setCellValue(device.getName());
                    row.createCell(2).setCellValue(device.getMgmtAddress().getIp());
                    row.createCell(3).setCellValue(device.getMgmtDomain().getName());
                    row.createCell(4).setCellValue(device.getNetworkClass().toString());
                    row.createCell(5).setCellValue(device.getFamily());
                    cell = row.createCell(6);
                    cell.setCellValue(device.getCreatedDate());
                    cell.setCellStyle(datetimeCellStyle);
                    cell = row.createCell(7);
                    cell.setCellValue(device.getChangeDate());
                    cell.setCellStyle(datetimeCellStyle);
                    row.createCell(8).setCellValue(device.getSoftwareVersion());
                    if (device.getEosDate() != null) {
                        cell = row.createCell(9);
                        cell.setCellValue(device.getEosDate());
                        cell.setCellStyle(dateCellStyle);
                    }
                    if (device.getEolDate() != null) {
                        cell = row.createCell(10);
                        cell.setCellValue(device.getEolDate());
                        cell.setCellStyle(dateCellStyle);
                    }
                }

                if (exportInterfaces) {
                    Sheet interfaceSheet = workBook.createSheet("Interfaces");
                    row = interfaceSheet.createRow(0);
                    row.createCell(0).setCellValue("Device ID");
                    row.createCell(1).setCellValue("Virtual Device");
                    row.createCell(2).setCellValue("Name");
                    row.createCell(3).setCellValue("Description");
                    row.createCell(4).setCellValue("VRF");
                    row.createCell(5).setCellValue("MAC Address");
                    row.createCell(6).setCellValue("Enabled");
                    row.createCell(7).setCellValue("Level 3");
                    row.createCell(8).setCellValue("IP Address");
                    row.createCell(9).setCellValue("Mask Length");
                    row.createCell(10).setCellValue("Usage");

                    int yInterface = 1;
                    for (Device device : devices) {
                        for (NetworkInterface networkInterface : device.getNetworkInterfaces()) {
                            if (networkInterface.getIpAddresses().size() == 0) {
                                row = interfaceSheet.createRow(yInterface++);
                                row.createCell(0).setCellValue(device.getId());
                                row.createCell(1).setCellValue(networkInterface.getVirtualDevice());
                                row.createCell(2).setCellValue(networkInterface.getInterfaceName());
                                row.createCell(3).setCellValue(networkInterface.getDescription());
                                row.createCell(4).setCellValue(networkInterface.getVrfInstance());
                                row.createCell(5).setCellValue(networkInterface.getMacAddress());
                                row.createCell(6).setCellValue(networkInterface.isEnabled());
                                row.createCell(7).setCellValue(networkInterface.isLevel3());
                                row.createCell(8).setCellValue("");
                                row.createCell(9).setCellValue("");
                                row.createCell(10).setCellValue("");
                            }
                            for (NetworkAddress address : networkInterface.getIpAddresses()) {
                                row = interfaceSheet.createRow(yInterface++);
                                row.createCell(0).setCellValue(device.getId());
                                row.createCell(1).setCellValue(networkInterface.getVirtualDevice());
                                row.createCell(2).setCellValue(networkInterface.getInterfaceName());
                                row.createCell(3).setCellValue(networkInterface.getDescription());
                                row.createCell(4).setCellValue(networkInterface.getVrfInstance());
                                row.createCell(5).setCellValue(networkInterface.getMacAddress());
                                row.createCell(6).setCellValue(networkInterface.isEnabled());
                                row.createCell(7).setCellValue(networkInterface.isLevel3());
                                row.createCell(8).setCellValue(address.getIp());
                                row.createCell(9).setCellValue(address.getPrefixLength());
                                row.createCell(10).setCellValue(address.getAddressUsage() == null ? "" : address.getAddressUsage().toString());
                            }
                        }
                    }
                }

                if (exportInventory) {
                    Sheet inventorySheet = workBook.createSheet("Inventory");
                    row = inventorySheet.createRow(0);
                    row.createCell(0).setCellValue("Device ID");
                    row.createCell(1).setCellValue("Slot");
                    row.createCell(2).setCellValue("Part Number");
                    row.createCell(3).setCellValue("Serial Number");

                    int yInventory = 1;
                    for (Device device : devices) {
                        for (Module module : device.getModules()) {
                            row = inventorySheet.createRow(yInventory++);
                            row.createCell(0).setCellValue(device.getId());
                            row.createCell(1).setCellValue(module.getSlot());
                            row.createCell(2).setCellValue(module.getPartNumber());
                            row.createCell(3).setCellValue(module.getSerialNumber());
                        }
                    }
                }

                ByteArrayOutputStream output = new ByteArrayOutputStream();
                workBook.write(output);
                workBook.close();
                return Response.ok(output.toByteArray()).header("Content-Disposition", "attachment; filename=" + fileName).build();
            } catch (IOException e) {
                logger.error("Unable to write the resulting file.", e);
                throw new WebApplicationException(
                        "Unable to write the resulting file.",
                        javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR);
            } catch (Exception e) {
                logger.error("Unable to generate the report.", e);
                throw new WebApplicationException("Unable to generate the report.",
                        javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR);
            } finally {
                session.close();
            }
        }

        logger.warn("Invalid requested file format.");
        throw new WebApplicationException(
                "The requested file format is invalid or not supported.",
                javax.ws.rs.core.Response.Status.BAD_REQUEST);

    }

    @POST
    @Path("scripts")
    @RolesAllowed("readwrite")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public DeviceJsScript addScript(@Context HttpServletRequest request, DeviceJsScript rsScript) throws WebApplicationException {
        logger.debug("REST request, add device script.");
        DeviceDriver driver = DeviceDriver.getDriverByName(rsScript.getDeviceDriver());
        if (driver == null) {
            logger.warn("Invalid driver name.");
            throw new MappingHttp.NetshotBadRequestException("Invalid driver name.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_SCRIPT);
        }
        if (rsScript.getName() == null || rsScript.getName().trim().equals("")) {
            logger.warn("Invalid script name.");
            throw new MappingHttp.NetshotBadRequestException("Invalid script name.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_SCRIPT);
        }
        if (rsScript.getScript() == null) {
            logger.warn("Invalid script.");
            throw new MappingHttp.NetshotBadRequestException("The script content can't be empty.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_SCRIPT);
        }
        try {
            User user = (User) request.getSession().getAttribute("user");
            rsScript.setAuthor(user.getUsername());
        } catch (Exception e) {
        }
        rsScript.setId(0);

        Session session = Database.getSession();
        try {
            session.beginTransaction();
            session.save(rsScript);
            session.getTransaction().commit();
            return rsScript;
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Error while saving the new rule.", e);
            Throwable t = e.getCause();
            if (t != null && t.getMessage().contains("Duplicate entry")) {
                throw new MappingHttp.NetshotBadRequestException(
                        "A script with this name already exists.",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DUPLICATE_SCRIPT);
            }
            throw new MappingHttp.NetshotBadRequestException(
                    "Unable to add the script to the database",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    @DELETE
    @Path("scripts/{id}")
    @RolesAllowed("readwrite")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public void deleteScript(@PathParam("id") Long id)
            throws WebApplicationException {
        logger.debug("REST request, delete script {}.", id);
        Session session = Database.getSession();
        try {
            session.beginTransaction();
            DeviceJsScript script = (DeviceJsScript) session.load(DeviceJsScript.class, id);
            session.delete(script);
            session.getTransaction().commit();
        } catch (ObjectNotFoundException e) {
            session.getTransaction().rollback();
            logger.error("The script {} to be deleted doesn't exist.", id, e);
            throw new MappingHttp.NetshotBadRequestException("The script doesn't exist.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_SCRIPT);
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Unable to delete the script {}.", id, e);
            throw new MappingHttp.NetshotBadRequestException("Unable to delete the script.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    @GET
    @Path("scripts/{id}")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public DeviceJsScript getScript(@PathParam("id") Long id) {
        logger.debug("REST request, get script {}", id);
        Session session = Database.getSession();
        try {
            DeviceJsScript script = (DeviceJsScript) session.get(DeviceJsScript.class, id);
            return script;
        } catch (ObjectNotFoundException e) {
            logger.error("Unable to find the script {}.", id, e);
            throw new MappingHttp.NetshotBadRequestException("Script not found.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_SCRIPT);
        } catch (HibernateException e) {
            logger.error("Unable to fetch the script {}.", id, e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the script.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    @GET
    @Path("scripts")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List<DeviceJsScript> getScripts() {
        logger.debug("REST request, get scripts.");
        Session session = Database.getSession();
        try {
            @SuppressWarnings("unchecked")
            List<DeviceJsScript> scripts = session.createQuery("from DeviceJsScript s").list();
            for (DeviceJsScript script : scripts) {
                script.setScript(null);
            }
            return scripts;
        } catch (HibernateException e) {
            logger.error("Unable to fetch the scripts.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the scripts",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /********************************************************************************************************************/


    /**
     * Gets the Virtual Device.
     *
     * @return the Virtual Device
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("scp/device")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Object getVirtual() {
        Session s = Database.getSession();
        try {
            List el = s.createCriteria(VirtualDevice.class).list();
            Set<Object> mySet = new HashSet<Object>();
            mySet.addAll(el);
            return mySet;
        } catch (HibernateException e) {
            logger.error("Unable to fetch the virutalDevice.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the virutalDevice",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            s.close();
        }
    }

    /**
     * Return the Virtual Device by ID.
     *
     * @param id ID of the VirtualDevice
     * @return the Virtual Device
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("scp/device/{id}")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public VirtualDevice getVirtualDeviceById(@PathParam("id") Long id) {
        Session session = Database.getSession();
        VirtualDevice v;
        try {
            session.beginTransaction();
            v = (VirtualDevice) session.get(VirtualDevice.class, id);
            return v;
        } catch (ObjectNotFoundException e) {
            logger.error("The VirutalDevice doesn't exist.", e);
            throw new MappingHttp.NetshotBadRequestException("The VirutalDevice doesn't exist.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DOMAIN);
        } catch (HibernateException e) {
            session.getTransaction().rollback();
            logger.error("Error while editing the virtualDevice.", e);
            throw new MappingHttp.NetshotBadRequestException(
                    "Unable to save the domain... is the name already in use?",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Return the companies.
     *
     * @return the Companies
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("scp/company")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List getAllCompany() {
        Session session = Database.getSession();
        List company = new ArrayList();
        try {
            session.beginTransaction();
            company = session.createCriteria(Company.class).list();
            return company;
        } catch (ObjectNotFoundException e) {
            return company;
        } catch (HibernateException e) {
            logger.error("Error while getting companies.", e);
            throw new MappingHttp.NetshotBadRequestException(
                    "Error while getting companies.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    /**
     * Return the all type.
     *
     * @return the Companies
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("scp/type")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public List getAllType() {
        Session session = Database.getSession();
        List type = new ArrayList();
        try {
            session.beginTransaction();
            type = session.createCriteria(Types.class).list();
            return type;
        } catch (ObjectNotFoundException e) {
            return type;
        } catch (HibernateException e) {
            logger.error("Error while getting type.", e);
            throw new MappingHttp.NetshotBadRequestException(
                    "Error while getting type.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }


    @GET
    @Path("scp/file/{id}")
    @RolesAllowed("readonly")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Object getConfigurationVirtualDevice(@PathParam("id") Long id) {
        logger.debug("REST request, get virutal device {id}.", id);
        Session session = Database.getSession();
        String destPath = Netshot.getConfig("netshot.watch.moveFile");
        try {
            ScpStepFolder scp = (ScpStepFolder) session.get(ScpStepFolder.class, id);
            if (scp == null) {
                logger.warn("Unable to find the scp object.");
                throw new WebApplicationException(
                        "Unable to find the scp set",
                        javax.ws.rs.core.Response.Status.NOT_FOUND);
            }
            VirtualDevice vs = scp.getVirtual();
            if (vs != null) {
                java.nio.file.Path dest = generatePath(destPath, vs.getFolder());
                java.nio.file.Path tmpDest = Paths.get(dest.toAbsolutePath().toString() + '/' +
                        vs.getId() + "_" + generateDateSave(scp.getCreated()) + '_' + scp.getNameFile());

                StreamingOutput fileStream = output -> {
                    byte[] data = Files.readAllBytes(tmpDest);
                    output.write(data);
                    output.flush();
                };
                return Response.ok(fileStream, MediaType.APPLICATION_OCTET_STREAM)
                        .header("Content-Disposition", "attachment; filename=" + scp.getNameFile())
                        .build();
            }
            throw new WebApplicationException("Unable to get the configuration",
                    javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR);
        } catch (HibernateException e) {
            throw new WebApplicationException("Unable to get the configuration",
                    javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR);
        } finally {
            session.close();
        }
    }


    /**
     * Gets the user SSH.
     *
     * @return the Virtual Device
     * @throws WebApplicationException the web application exception
     */
    @GET
    @Path("users/ssh")
    @RolesAllowed("admin")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Object getUsersSsh() {
        Session s = Database.getSession();
        try {
            List el = s.createCriteria(UserSsh.class).list();
            Set<Object> mySet = new HashSet<Object>();
            mySet.addAll(el);
            return mySet;
        } catch (HibernateException e) {
            logger.error("Unable to fetch the UserSSH.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to fetch the UserSSH",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            s.close();
        }
    }


    @POST
    @Path("scp/device")
    @RolesAllowed("readwrite")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public VirtualDevice addDeviceVitual(@Context HttpServletRequest request, MappingHttp.RsNewDeviceVirtual rsNewDeviceVirtual) {
        Session session = Database.getSession();
        Transaction tx = null;
        try {
            tx = session.beginTransaction();
            Company c = (Company) session.get(Company.class, rsNewDeviceVirtual.getCompany());
            if (c != null) {
                List o = session.createQuery("from VirtualDevice vd left join vd.company c " +
                        "where vd.name = :cName AND c.name = :companyName")
                        .setParameter("cName", rsNewDeviceVirtual.getName())
                        .setParameter("companyName", c.getName())
                        .list();
                if (o.size() == 0) {
                    Types t = (Types) session.get(Types.class, (long) rsNewDeviceVirtual.getType());
                    if (t != null) {
                        rsNewDeviceVirtual.setFolder(rsNewDeviceVirtual.getFolder().replaceAll("[^a-zA-Z0-9.-/]", "_"));
                        if (VirtualDevice.createFolder(rsNewDeviceVirtual.getFolder())) {
                            VirtualDevice newVd = new VirtualDevice(rsNewDeviceVirtual.getName(), rsNewDeviceVirtual.getFolder());
                            newVd.setCompany(c);
                            newVd.setType(t);
                            String newDate = rsNewDeviceVirtual.getDate() + " " + rsNewDeviceVirtual.getHour();
                            Date d = new SimpleDateFormat("dd/MM/yyyy hh:mm a").parse(newDate);
                            switch (rsNewDeviceVirtual.getTask()) {
                                case "HOUR":
                                    newVd.setCron(VirtualDevice.CRON.HOUR);
                                    newVd.setHour(d);
                                    session.save(newVd);
                                    tx.commit();

                                    generateTaskHourly(newVd);
                                    break;
                                case "DAILY": {
                                    newVd.setCron(VirtualDevice.CRON.DAILY);
                                    newVd.setHour(d);
                                    session.save(newVd);
                                    tx.commit();

                                    generateTaskDaily(newVd);
                                    break;
                                }
                                case "WEEKLY": {
                                    newVd.setCron(VirtualDevice.CRON.WEEKLY);
                                    newVd.setHour(d);
                                    session.save(newVd);
                                    tx.commit();

                                    generateTaskWeekly(newVd);
                                    break;
                                }
                            }
                            return newVd;
                        } else
                            throw new MappingHttp.NetshotBadRequestException("Directory cannot be create",
                                    MappingHttp.NetshotBadRequestException.NETSHOT_ERROR_CREATEFOLDER);
                    } else {
                        throw new MappingHttp.NetshotBadRequestException("This Types does not exists",
                                MappingHttp.NetshotBadRequestException.NETSHOT_DUPLICATE_DEVICE);
                    }
                } else {
                    throw new MappingHttp.NetshotBadRequestException("This ip is already set to an Applicance",
                            MappingHttp.NetshotBadRequestException.NETSHOT_DUPLICATE_DEVICE);
                }
            } else {
                throw new MappingHttp.NetshotBadRequestException("This company does not exist ",
                        MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DEVICE);
            }
        } catch (HibernateException e) {
            tx.rollback();
            logger.error("Unable to add this virtualDevice.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to add this virtualDevice",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } catch (ParseException e) {
            throw new MappingHttp.NetshotBadRequestException("Error parse Date",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    @POST
    @Path("scp/company")
    @RolesAllowed("readwrite")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Company addCompany(@Context HttpServletRequest request, MappingHttp.RsNewCompany rsNewCompany) {
        Session session = Database.getSession();
        Transaction tx = null;
        try {
            tx = session.beginTransaction();
            Company c = (Company) session.createQuery("from Company c where c.name = :name")
                    .setParameter("name", rsNewCompany.getName())
                    .uniqueResult();
            if (c == null) {
                Company newC = new Company();
                newC.setName(rsNewCompany.getName());
                session.save(newC);
                tx.commit();
                return newC;
            } else {
                throw new MappingHttp.NetshotBadRequestException("This Company already exists",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DUPLICATE_DEVICE);
            }
        } catch (HibernateException e) {
            tx.rollback();
            logger.error("Unable to add this Company.", e);
            throw new MappingHttp.NetshotBadRequestException("Unable to add this Company",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }


    @POST
    @Path("users/ssh")
    @RolesAllowed("admin")
    @Consumes({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public UserSsh addUserSsh(@Context HttpServletRequest request, MappingHttp.RsNewuserSsh rsNewuserSsh) {
        Session session = Database.getSession();
        Transaction tx = null;
        if ((rsNewuserSsh.getCertificat() != null && !rsNewuserSsh.getCertificat().equals("")) ||
                (rsNewuserSsh.getPassword() != null && !rsNewuserSsh.getPassword().equals(""))) {
            try {
                tx = session.beginTransaction();
                UserSsh u = (UserSsh) session.createQuery("from UserSsh u where u.name = :name")
                        .setParameter("name", rsNewuserSsh.getName())
                        .uniqueResult();
                if (u == null) {
                    UserSsh newU = new UserSsh();
                    newU.setName(rsNewuserSsh.getName());
                    if (rsNewuserSsh.getCertificat() != null && !rsNewuserSsh.getCertificat().equals(""))
                        newU.setCertificat(rsNewuserSsh.getCertificat());
                    if (rsNewuserSsh.getPassword() != null && !rsNewuserSsh.getPassword().equals(""))
                        newU.setPassword(rsNewuserSsh.getPassword());
                    session.save(newU);
                    tx.commit();
                    return newU;
                } else {
                    throw new MappingHttp.NetshotBadRequestException("This user already exists",
                            MappingHttp.NetshotBadRequestException.NETSHOT_DUPLICATE_DEVICE);
                }
            } catch (HibernateException e) {
                tx.rollback();
                logger.error("Unable to add this user.", e);
                throw new MappingHttp.NetshotBadRequestException("Unable to add this user",
                        MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
            } finally {
                session.close();
            }
        } else {
            throw new MappingHttp.NetshotBadRequestException("One password or one certificat must be set",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INCOMPATIBLE_CONFIGS);
        }
    }

    @DELETE
    @Path("scp/device/{id}")
    @RolesAllowed("admin")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public void deleteDevice(@PathParam("id") Integer id) {
        Session session = Database.getSession();
        Transaction tx = null;
        try {
            tx = session.beginTransaction();
            VirtualDevice vs = (VirtualDevice) session.get(VirtualDevice.class, (long) id);
            if (vs != null) {
                for (ScpStepFolder s : vs.getFile()) {
                    session.delete(s);
                }
                java.nio.file.Path folder = generatePathDest(vs.getFolder());
                java.nio.file.Path folderDdl = generatePathMove(vs.getFolder());
                vs.setCompany(null);
                session.delete(vs);
                tx.commit();
                // Delete the backup film from this virtual device
                deleteFolder(folder.toAbsolutePath().toFile());
                deleteFolder(folderDdl.toAbsolutePath().toFile());
            }

        } catch (ObjectNotFoundException e) {
            throw new MappingHttp.NetshotBadRequestException(
                    "Virtual device not found",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DEVICE);
        } catch (HibernateException e) {
            tx.rollback();
            logger.error("Error while getting type.", e);
            throw new MappingHttp.NetshotBadRequestException(
                    "Error while getting type.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }

    @DELETE
    @Path("users/ssh/{id}")
    @RolesAllowed("admin")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public void deleteUserSSH(@PathParam("id") Integer id) {
        Session session = Database.getSession();
        Transaction tx = null;
        try {
            tx = session.beginTransaction();
            UserSsh vs = (UserSsh) session.get(UserSsh.class, (long) id);
            if (vs != null) {
                session.delete(vs);
                tx.commit();
            }
        } catch (ObjectNotFoundException e) {
            throw new MappingHttp.NetshotBadRequestException(
                    "User SSH not found",
                    MappingHttp.NetshotBadRequestException.NETSHOT_INVALID_DEVICE);
        } catch (HibernateException e) {
            tx.rollback();
            logger.error("Error while getting type.", e);
            throw new MappingHttp.NetshotBadRequestException(
                    "Error while getting type.",
                    MappingHttp.NetshotBadRequestException.NETSHOT_DATABASE_ACCESS_ERROR);
        } finally {
            session.close();
        }
    }


}
