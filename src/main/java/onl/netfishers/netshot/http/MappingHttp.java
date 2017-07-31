package onl.netfishers.netshot.http;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import difflib.Delta;
import onl.netfishers.netshot.RestService;
import onl.netfishers.netshot.aaa.User;
import onl.netfishers.netshot.compliance.CheckResult;
import onl.netfishers.netshot.compliance.SoftwareRule;
import onl.netfishers.netshot.device.Device;
import onl.netfishers.netshot.device.Domain;
import onl.netfishers.netshot.device.Network4Address;
import onl.netfishers.netshot.work.Task;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Priority;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.Priorities;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ExceptionMapper;
import javax.xml.bind.annotation.*;
import java.io.IOException;
import java.security.Principal;
import java.util.*;

/**
 * Created by agm on 26/06/2017.
 */
public class MappingHttp {

    /**
     * The logger.
     */
    private static Logger logger = LoggerFactory
            .getLogger(MappingHttp.class);

    /**
     * The Class RsDomain.
     */
    @XmlRootElement(name = "domain")
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsDomain {

        /**
         * The id.
         */
        private long id = -1;

        /**
         * The name.
         */
        private String name = "";

        /**
         * The description.
         */
        private String description = "";

        /**
         * The ip address.
         */
        private String ipAddress = "";

        /**
         * Instantiates a new rs domain.
         */
        public RsDomain() {

        }

        /**
         * Instantiates a new rs domain.
         *
         * @param domain the domain
         */
        public RsDomain(Domain domain) {
            this.id = domain.getId();
            this.name = domain.getName();
            this.description = domain.getDescription();
            this.ipAddress = domain.getServer4Address().getIp();
        }

        /**
         * Gets the id.
         *
         * @return the id
         */
        @XmlElement
        public long getId() {
            return id;
        }

        /**
         * Sets the id.
         *
         * @param id the new id
         */
        public void setId(long id) {
            this.id = id;
        }

        /**
         * Gets the name.
         *
         * @return the name
         */
        @XmlElement
        public String getName() {
            return name;
        }

        /**
         * Sets the name.
         *
         * @param name the new name
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * Gets the description.
         *
         * @return the description
         */
        @XmlElement
        public String getDescription() {
            return description;
        }

        /**
         * Sets the description.
         *
         * @param description the new description
         */
        public void setDescription(String description) {
            this.description = description;
        }

        /**
         * Gets the ip address.
         *
         * @return the ip address
         */
        @XmlElement
        public String getIpAddress() {
            return ipAddress;
        }

        /**
         * Sets the ip address.
         *
         * @param ipAddress the new ip address
         */
        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }

    }

    /**
     * The Class RsConfigDiff.
     */
    @XmlRootElement
    public static class RsConfigDiff {

        /**
         * The original date.
         */
        private Date originalDate;

        /**
         * The revised date.
         */
        private Date revisedDate;

        /**
         * The deltas.
         */
        private Map<String, List<RsConfigDelta>> deltas = new HashMap<String, List<RsConfigDelta>>();

        /**
         * Instantiates a new rs config diff.
         *
         * @param originalDate the original date
         * @param revisedDate  the revised date
         */
        public RsConfigDiff(Date originalDate, Date revisedDate) {
            this.originalDate = originalDate;
            this.revisedDate = revisedDate;
        }

        /**
         * Adds the delta.
         *
         * @param item  the item
         * @param delta the delta
         */
        public void addDelta(String item, RsConfigDelta delta) {
            if (!deltas.containsKey(item)) {
                deltas.put(item, new ArrayList<RsConfigDelta>());
            }
            deltas.get(item).add(delta);
        }

        /**
         * Gets the original date.
         *
         * @return the original date
         */
        @XmlElement
        public Date getOriginalDate() {
            return originalDate;
        }

        /**
         * Gets the revised date.
         *
         * @return the revised date
         */
        @XmlElement
        public Date getRevisedDate() {
            return revisedDate;
        }

        /**
         * Gets the deltas.
         *
         * @return the deltas
         */
        @XmlElement
        public Map<String, List<RsConfigDelta>> getDeltas() {
            return deltas;
        }
    }

    /**
     * The Class RsConfigDelta.
     */
    @XmlRootElement
    public static class RsConfigDelta {

        /**
         * The item.
         */
        private String item;
        /**
         * The diff type.
         */
        private Type diffType;
        /**
         * The original position.
         */
        private int originalPosition;
        /**
         * The revised position.
         */
        private int revisedPosition;
        /**
         * The original lines.
         */
        private List<String> originalLines;
        /**
         * The revised lines.
         */
        private List<String> revisedLines;
        /**
         * The pre context.
         */
        private List<String> preContext;
        /**
         * The post context.
         */
        private List<String> postContext;

        /**
         * Instantiates a new rs config delta.
         *
         * @param delta   the delta
         * @param context the context
         */
        public RsConfigDelta(Delta<String> delta, List<String> context) {
            switch (delta.getType()) {
                case INSERT:
                    this.diffType = Type.INSERT;
                    break;
                case DELETE:
                    this.diffType = Type.DELETE;
                    break;
                case CHANGE:
                default:
                    this.diffType = Type.CHANGE;
            }
            this.originalPosition = delta.getOriginal().getPosition();
            this.originalLines = delta.getOriginal().getLines();
            this.revisedPosition = delta.getRevised().getPosition();
            this.revisedLines = delta.getRevised().getLines();
            this.preContext = context.subList(Math.max(this.originalPosition - 3, 0),
                    this.originalPosition);
            this.postContext = context.subList(Math.min(this.originalPosition
                            + this.originalLines.size(), context.size() - 1),
                    Math.min(this.originalPosition + this.originalLines.size() + 3,
                            context.size() - 1));
        }

        /**
         * Gets the diff type.
         *
         * @return the diff type
         */
        @XmlElement
        public Type getDiffType() {
            return diffType;
        }

        /**
         * Gets the original position.
         *
         * @return the original position
         */
        @XmlElement
        public int getOriginalPosition() {
            return originalPosition;
        }

        /**
         * Gets the revised position.
         *
         * @return the revised position
         */
        @XmlElement
        public int getRevisedPosition() {
            return revisedPosition;
        }

        /**
         * Gets the original lines.
         *
         * @return the original lines
         */
        @XmlElement
        public List<String> getOriginalLines() {
            return originalLines;
        }

        /**
         * Gets the revised lines.
         *
         * @return the revised lines
         */
        @XmlElement
        public List<String> getRevisedLines() {
            return revisedLines;
        }

        /**
         * Gets the item.
         *
         * @return the item
         */
        @XmlElement
        public String getItem() {
            return item;
        }

        /**
         * Gets the pre context.
         *
         * @return the pre context
         */
        @XmlElement
        public List<String> getPreContext() {
            return preContext;
        }

        /**
         * Gets the post context.
         *
         * @return the post context
         */
        @XmlElement
        public List<String> getPostContext() {
            return postContext;
        }

        /**
         * The Enum Type.
         */
        public static enum Type {

            /**
             * The change.
             */
            CHANGE,

            /**
             * The delete.
             */
            DELETE,

            /**
             * The insert.
             */
            INSERT;
        }
    }

    /**
     * The Class RsLightDevice.
     */
    @XmlRootElement
    @XmlAccessorType(value = XmlAccessType.NONE)
    public static class RsLightDevice {

        /**
         * The id.
         */
        private long id;

        /**
         * The name.
         */
        private String name;

        /**
         * The family.
         */
        private String family;

        /**
         * The mgmt address.
         */
        private Network4Address mgmtAddress;

        /**
         * The status.
         */
        private Device.Status status;

        /**
         * Gets the id.
         *
         * @return the id
         */
        @XmlElement
        public long getId() {
            return id;
        }

        /**
         * Sets the id.
         *
         * @param id the new id
         */
        public void setId(long id) {
            this.id = id;
        }

        /**
         * Gets the name.
         *
         * @return the name
         */
        @XmlElement
        public String getName() {
            return name;
        }

        /**
         * Sets the name.
         *
         * @param name the new name
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * Gets the family.
         *
         * @return the family
         */
        @XmlElement
        public String getFamily() {
            return family;
        }

        /**
         * Sets the family.
         *
         * @param family the new family
         */
        public void setFamily(String family) {
            this.family = family;
        }

        /**
         * Gets the mgmt address.
         *
         * @return the mgmt address
         */
        @XmlElement
        public Network4Address getMgmtAddress() {
            return mgmtAddress;
        }

        /**
         * Sets the mgmt address.
         *
         * @param mgmtAddress the new mgmt address
         */
        public void setMgmtAddress(Network4Address mgmtAddress) {
            this.mgmtAddress = mgmtAddress;
        }

        /**
         * Gets the status.
         *
         * @return the status
         */
        @XmlElement
        public Device.Status getStatus() {
            return status;
        }

        /**
         * Sets the status.
         *
         * @param status the new status
         */
        public void setStatus(Device.Status status) {
            this.status = status;
        }


    }

    /**
     * The Class RsDeviceFamily.
     */
    @XmlRootElement(name = "deviceType")
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsDeviceFamily {

        /**
         * The device type.
         */
        private String driver;

        /**
         * The device family.
         */
        private String deviceFamily;

        @XmlElement
        public String getDriver() {
            return driver;
        }

        public void setDriver(String driver) {
            this.driver = driver;
        }

        /**
         * Gets the device family.
         *
         * @return the device family
         */
        @XmlElement
        public String getDeviceFamily() {
            return deviceFamily;
        }

        /**
         * Sets the device family.
         *
         * @param deviceFamily the new device family
         */
        public void setDeviceFamily(String deviceFamily) {
            this.deviceFamily = deviceFamily;
        }
    }

    @XmlRootElement(name = "partNumber")
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsPartNumber {
        private String partNumber;

        @XmlElement
        public String getPartNumber() {
            return partNumber;
        }

        public void setPartNumber(String partNumber) {
            this.partNumber = partNumber;
        }
    }

    /**
     * The Class RsNewDevice.
     */
    @XmlRootElement(name = "device")
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsNewDevice {

        private String emails;
        private Boolean onSuccess = false;
        private Boolean onError = false;

        /**
         * The auto discover.
         */
        private boolean autoDiscover = true;

        /**
         * The auto discovery task.
         */
        private long autoDiscoveryTask = 0;

        /**
         * The ip address.
         */
        private String ipAddress = "";

        /**
         * The Path Configuration. AGM
         */
        private String pathConfiguration = "/";
        private Integer retention = null;

        /**
         * The domain id.
         */
        private long domainId = -1;

        /**
         * The name.
         */
        private String name = "";

        /**
         * The device type.
         */
        private String deviceType = "";

        /**
         * Checks if is auto discover.
         *
         * @return true, if is auto discover
         */
        @XmlElement
        public boolean isAutoDiscover() {
            return autoDiscover;
        }

        /**
         * Sets the auto discover.
         *
         * @param autoDiscover the new auto discover
         */
        public void setAutoDiscover(boolean autoDiscover) {
            this.autoDiscover = autoDiscover;
        }

        /**
         * Gets the auto discovery task.
         *
         * @return the auto discovery task
         */
        @XmlElement
        public long getAutoDiscoveryTask() {
            return autoDiscoveryTask;
        }

        /**
         * Sets the auto discovery task.
         *
         * @param autoDiscoveryTask the new auto discovery task
         */
        public void setAutoDiscoveryTask(long autoDiscoveryTask) {
            this.autoDiscoveryTask = autoDiscoveryTask;
        }

        /**
         * Gets the ip address.
         *
         * @return the ip address
         */
        @XmlElement
        public String getIpAddress() {
            return ipAddress;
        }

        /**
         * Sets the ip address.
         *
         * @param ipAddress the new ip address
         */
        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }

        /**
         * Gets the domain id.
         *
         * @return the domain id
         */
        @XmlElement
        public long getDomainId() {
            return domainId;
        }

        /**
         * Sets the domain id.
         *
         * @param domainId the new domain id
         */
        public void setDomainId(long domainId) {
            this.domainId = domainId;
        }

        /**
         * Gets the name.
         *
         * @return the name
         */
        @XmlElement
        public String getName() {
            return name;
        }

        /**
         * Sets the name.
         *
         * @param name the new name
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * Gets the device type.
         *
         * @return the device type
         */
        @XmlElement
        public String getDeviceType() {
            return deviceType;
        }

        /**
         * Sets the device type.
         *
         * @param deviceType the new device type
         */
        public void setDeviceType(String deviceType) {
            this.deviceType = deviceType;
        }

        /**
         * Gets the configuraton path .
         *
         * @return the configuration path (relative)
         */
        @XmlElement
        public String getPathConfiguration() {
            return pathConfiguration;
        }

        public void setPathConfiguration(String pathConfiguration) {
            this.pathConfiguration = pathConfiguration;
        }

        @XmlElement
        public Integer getRetention() {
            return retention;
        }

        public void setRetention(Integer retention) {
            this.retention = retention;
        }

        @XmlElement
        public String getEmails() {
            return emails;
        }

        public void setEmails(String emails) {
            this.emails = emails;
        }

        @XmlElement
        public Boolean getOnSuccess() {
            return onSuccess;
        }

        public void setOnSuccess(Boolean onSuccess) {
            this.onSuccess = onSuccess;
        }

        @XmlElement
        public Boolean getOnError() {
            return onError;
        }

        public void setOnError(Boolean onError) {
            this.onError = onError;
        }
    }

    /**
     * The Class RsDevice.
     */
    @XmlRootElement(name = "device")
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsDevice {

        /**
         * The id.
         */
        private long id = -1;

        /**
         * The enable.
         */
        private Boolean enabled = null;

        /**
         * The comments.
         */
        private String comments = null;

        /**
         * The ip address.
         */
        private String ipAddress = null;

        /**
         * The auto try credentials.
         */
        private Boolean autoTryCredentials = null;

        /**
         * The credential set ids.
         */
        private List<Long> credentialSetIds = null;

        private List<Long> clearCredentialSetIds = null;

        private Long mgmtDomain = null;

        /**
         * The configuration Path
         */
        private String pathConfiguration = null;


        private String emails = "";
        private Boolean onSuccess = false;
        private Boolean onError = false;

        /**
         * Instantiates a new rs device.
         */
        public RsDevice() {

        }

        @XmlElement
        public String getEmails() {
            return emails;
        }

        public void setEmails(String emails) {
            this.emails = emails;
        }

        @XmlElement
        public Boolean getOnSuccess() {
            return onSuccess;
        }

        public void setOnSuccess(Boolean onSuccess) {
            this.onSuccess = onSuccess;
        }

        @XmlElement
        public Boolean getOnError() {
            return onError;
        }

        public void setOnError(Boolean onError) {
            this.onError = onError;
        }

        /**
         * Gets the id.
         *
         * @return the id
         */
        @XmlElement
        public long getId() {
            return id;
        }

        /**
         * Sets the id.
         *
         * @param id the new id
         */
        public void setId(long id) {
            this.id = id;
        }

        /**
         * Gets the comments.
         *
         * @return the comments
         */
        @XmlElement
        public String getComments() {
            return comments;
        }

        /**
         * Sets the comments.
         *
         * @param comments the new comments
         */
        public void setComments(String comments) {
            this.comments = comments;
        }

        /**
         * Gets the ip address.
         *
         * @return the ip address
         */
        @XmlElement
        public String getIpAddress() {
            return ipAddress;
        }

        /**
         * Sets the ip address.
         *
         * @param ipAddress the new ip address
         */
        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }

        /**
         * Checks if is auto try credentials.
         *
         * @return true, if is auto try credentials
         */
        @XmlElement
        public Boolean isAutoTryCredentials() {
            return autoTryCredentials;
        }

        /**
         * Sets the auto try credentials.
         *
         * @param autoTryCredentials the new auto try credentials
         */
        public void setAutoTryCredentials(Boolean autoTryCredentials) {
            this.autoTryCredentials = autoTryCredentials;
        }

        /**
         * Gets the credential set ids.
         *
         * @return the credential set ids
         */
        @XmlElement
        public List<Long> getCredentialSetIds() {
            return credentialSetIds;
        }

        /**
         * Sets the credential set ids.
         *
         * @param credentialSetIds the new credential set ids
         */
        public void setCredentialSetIds(List<Long> credentialSetIds) {
            this.credentialSetIds = credentialSetIds;
        }

        @XmlElement
        public String getPathConfiguration() {
            return pathConfiguration;
        }

        public void setPathConfiguration(String path) {
            this.pathConfiguration = path;
        }

        /**
         * Checks if is enable.
         *
         * @return true, if is enable
         */
        @XmlElement
        public Boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets the enable.
         *
         * @param enabled the new enable
         */
        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        @XmlElement
        public Long getMgmtDomain() {
            return mgmtDomain;
        }

        public void setMgmtDomain(Long mgmtDomain) {
            this.mgmtDomain = mgmtDomain;
        }

        @XmlElement
        public List<Long> getClearCredentialSetIds() {
            return clearCredentialSetIds;
        }

        public void setClearCredentialSetIds(List<Long> clearCredentialSetIds) {
            this.clearCredentialSetIds = clearCredentialSetIds;
        }
    }

    /**
     * The Class RsSearchCriteria.
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsSearchCriteria {

        /**
         * The device class name.
         */
        private String driver;

        /**
         * The query.
         */
        private String query;

        /**
         * Gets the device class name.
         *
         * @return the device class name
         */
        @XmlElement
        public String getDriver() {
            return driver;
        }

        public void setDriver(String driver) {
            this.driver = driver;
        }

        /**
         * Gets the query.
         *
         * @return the query
         */
        @XmlElement
        public String getQuery() {
            return query;
        }

        /**
         * Sets the query.
         *
         * @param query the new query
         */
        public void setQuery(String query) {
            this.query = query;
        }
    }

    /**
     * The Class RsSearchResults.
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsSearchResults {

        /**
         * The query.
         */
        private String query;

        /**
         * The devices.
         */
        private List<RsLightDevice> devices;

        /**
         * Gets the query.
         *
         * @return the query
         */
        @XmlElement
        public String getQuery() {
            return query;
        }

        /**
         * Sets the query.
         *
         * @param query the new query
         */
        public void setQuery(String query) {
            this.query = query;
        }

        /**
         * Gets the devices.
         *
         * @return the devices
         */
        @XmlElement
        public List<RsLightDevice> getDevices() {
            return devices;
        }

        /**
         * Sets the devices.
         *
         * @param devices the new devices
         */
        public void setDevices(List<RsLightDevice> devices) {
            this.devices = devices;
        }
    }

    /**
     * The Class RsDeviceGroup.
     */
    @XmlRootElement(name = "group")
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsDeviceGroup {

        /**
         * The id.
         */
        private long id = -1;

        /**
         * The type.
         */
        private String type;

        /**
         * The static devices.
         */
        private List<Long> staticDevices = new ArrayList<Long>();

        /**
         * The device class name.
         */
        private String driver;

        /**
         * The query.
         */
        private String query;

        /**
         * The folder.
         */
        private String folder = "";

        /**
         * Hide the group in reports.
         */
        private boolean hiddenFromReports = false;

        /**
         * Instantiates a new rs device group.
         */
        public RsDeviceGroup() {

        }

        /**
         * Gets the id.
         *
         * @return the id
         */
        @XmlElement
        public long getId() {
            return id;
        }

        /**
         * Sets the id.
         *
         * @param id the new id
         */
        public void setId(long id) {
            this.id = id;
        }

        /**
         * Gets the type.
         *
         * @return the type
         */
        @XmlElement
        public String getType() {
            return type;
        }

        /**
         * Sets the type.
         *
         * @param type the new type
         */
        public void setType(String type) {
            this.type = type;
        }

        /**
         * Gets the static devices.
         *
         * @return the static devices
         */
        @XmlElement
        public List<Long> getStaticDevices() {
            return staticDevices;
        }

        /**
         * Sets the static devices.
         *
         * @param staticDevices the new static devices
         */
        public void setStaticDevices(List<Long> staticDevices) {
            this.staticDevices = staticDevices;
        }

        /**
         * Gets the device class name.
         *
         * @return the device class name
         */
        @XmlElement
        public String getDriver() {
            return driver;
        }

        public void setDriver(String driver) {
            this.driver = driver;
        }

        /**
         * Gets the query.
         *
         * @return the query
         */
        @XmlElement
        public String getQuery() {
            return query;
        }

        /**
         * Sets the query.
         *
         * @param query the new query
         */
        public void setQuery(String query) {
            this.query = query;
        }

        @XmlElement
        public String getFolder() {
            return folder;
        }

        public void setFolder(String folder) {
            this.folder = folder;
        }

        @XmlElement
        public boolean isHiddenFromReports() {
            return hiddenFromReports;
        }

        public void setHiddenFromReports(boolean hiddenFromReports) {
            this.hiddenFromReports = hiddenFromReports;
        }

    }

    /**
     * The Class RsTask.
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsTask {

        /**
         * The id.
         */
        private long id;

        /**
         * The cancelled.
         */
        private boolean cancelled = false;

        /**
         * The type.
         */
        private String type = "";

        /**
         * The group.
         */
        private Long group = new Long(0);

        /**
         * The device.
         */
        private Long device = new Long(0);

        /**
         * The domain.
         */
        private Long domain = new Long(0);

        /**
         * The subnets.
         */
        private String subnets = "";

        /**
         * The IP addresses.
         */
        private String ipAddresses = "";

        /**
         * The schedule reference.
         */
        private Date scheduleReference = new Date();

        /**
         * The schedule type.
         */
        private Task.ScheduleType scheduleType = Task.ScheduleType.ASAP;

        /**
         * The comments.
         */
        private String comments = "";

        private int limitToOutofdateDeviceHours = -1;

        private int daysToPurge = 90;

        private int configDaysToPurge = -1;

        private int configSizeToPurge = 0;

        private int configKeepDays = 0;

        private String script = "";

        private String driver;

        /**
         * Gets the id.
         *
         * @return the id
         */
        @XmlElement
        public long getId() {
            return id;
        }

        /**
         * Sets the id.
         *
         * @param id the new id
         */
        public void setId(long id) {
            this.id = id;
        }

        /**
         * Checks if is cancelled.
         *
         * @return true, if is cancelled
         */
        @XmlElement
        public boolean isCancelled() {
            return cancelled;
        }

        /**
         * Sets the cancelled.
         *
         * @param cancelled the new cancelled
         */
        public void setCancelled(boolean cancelled) {
            this.cancelled = cancelled;
        }

        /**
         * Gets the type.
         *
         * @return the type
         */
        @XmlElement
        public String getType() {
            return type;
        }

        /**
         * Sets the type.
         *
         * @param type the new type
         */
        public void setType(String type) {
            this.type = type;
        }

        /**
         * Gets the group.
         *
         * @return the group
         */
        @XmlElement
        public Long getGroup() {
            return group;
        }

        /**
         * Sets the group.
         *
         * @param group the new group
         */
        public void setGroup(Long group) {
            this.group = group;
        }

        /**
         * Gets the device.
         *
         * @return the device
         */
        @XmlElement
        public Long getDevice() {
            return device;
        }

        /**
         * Sets the device.
         *
         * @param device the new device
         */
        public void setDevice(Long device) {
            this.device = device;
        }

        /**
         * Gets the subnets.
         *
         * @return the subnets
         */
        @XmlElement
        public String getSubnets() {
            return subnets;
        }

        /**
         * Sets the subnets.
         *
         * @param subnet the new subnets
         */
        public void setSubnets(String subnet) {
            this.subnets = subnet;
        }

        /**
         * Gets the schedule reference.
         *
         * @return the schedule reference
         */
        @XmlElement
        public Date getScheduleReference() {
            return scheduleReference;
        }

        /**
         * Sets the schedule reference.
         *
         * @param scheduleReference the new schedule reference
         */
        public void setScheduleReference(Date scheduleReference) {
            this.scheduleReference = scheduleReference;
        }

        /**
         * Gets the schedule type.
         *
         * @return the schedule type
         */
        @XmlElement
        public Task.ScheduleType getScheduleType() {
            return scheduleType;
        }

        /**
         * Sets the schedule type.
         *
         * @param scheduleType the new schedule type
         */
        public void setScheduleType(Task.ScheduleType scheduleType) {
            this.scheduleType = scheduleType;
        }

        /**
         * Gets the comments.
         *
         * @return the comments
         */
        @XmlElement
        public String getComments() {
            return comments;
        }

        /**
         * Sets the comments.
         *
         * @param comments the new comments
         */
        public void setComments(String comments) {
            this.comments = comments;
        }

        /**
         * Gets the domain.
         *
         * @return the domain
         */
        @XmlElement
        public Long getDomain() {
            return domain;
        }

        /**
         * Sets the domain.
         *
         * @param domain the new domain
         */
        public void setDomain(Long domain) {
            this.domain = domain;
        }

        /**
         * Gets the ip addresses.
         *
         * @return the ip addresses
         */
        public String getIpAddresses() {
            return ipAddresses;
        }

        /**
         * Sets the ip addresses.
         *
         * @param ipAddresses the new ip addresses
         */
        public void setIpAddresses(String ipAddresses) {
            this.ipAddresses = ipAddresses;
        }

        /**
         * Gets the limit to outofdate device hours.
         *
         * @return the limit to outofdate device hours
         */
        @XmlElement
        public int getLimitToOutofdateDeviceHours() {
            return limitToOutofdateDeviceHours;
        }

        public void setLimitToOutofdateDeviceHours(int limitToOutofdateDeviceHours) {
            this.limitToOutofdateDeviceHours = limitToOutofdateDeviceHours;
        }

        @XmlElement
        public int getDaysToPurge() {
            return daysToPurge;
        }

        public void setDaysToPurge(int days) {
            this.daysToPurge = days;
        }

        @XmlElement
        public String getScript() {
            return script;
        }

        public void setScript(String script) {
            this.script = script;
        }

        @XmlElement
        public String getDriver() {
            return driver;
        }

        public void setDriver(String driver) {
            this.driver = driver;
        }

        @XmlElement
        public int getConfigDaysToPurge() {
            return configDaysToPurge;
        }

        public void setConfigDaysToPurge(int configDaysToPurge) {
            this.configDaysToPurge = configDaysToPurge;
        }

        @XmlElement
        public int getConfigSizeToPurge() {
            return configSizeToPurge;
        }

        public void setConfigSizeToPurge(int configSizeToPurge) {
            this.configSizeToPurge = configSizeToPurge;
        }

        @XmlElement
        public int getConfigKeepDays() {
            return configKeepDays;
        }

        public void setConfigKeepDays(int configKeepDays) {
            this.configKeepDays = configKeepDays;
        }

    }

    /**
     * The Class RsTaskCriteria.
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsTaskCriteria {

        /**
         * The status.
         */
        private String status = "";

        /**
         * The day.
         */
        private Date day = new Date();

        /**
         * Gets the status.
         *
         * @return the status
         */
        @XmlElement
        public String getStatus() {
            return status;
        }

        /**
         * Sets the status.
         *
         * @param status the new status
         */
        public void setStatus(String status) {
            this.status = status;
        }

        /**
         * Gets the day.
         *
         * @return the day
         */
        @XmlElement
        public Date getDay() {
            return day;
        }

        /**
         * Sets the day.
         *
         * @param day the new day
         */
        public void setDay(Date day) {
            this.day = day;
        }
    }

    /**
     * The Class RsConfigChange.
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsConfigChange {

        /**
         * The device name.
         */
        private String deviceName;

        /**
         * The device id.
         */
        private long deviceId;

        /**
         * The old change date.
         */
        private Date oldChangeDate;

        /**
         * The new change date.
         */
        private Date newChangeDate;

        /**
         * The author.
         */
        private String author;

        /**
         * The old id.
         */
        private long oldId = 0L;

        /**
         * The new id.
         */
        private long newId = 0L;

        /**
         * Gets the device name.
         *
         * @return the device name
         */
        @XmlElement
        public String getDeviceName() {
            return deviceName;
        }

        /**
         * Sets the device name.
         *
         * @param deviceName the new device name
         */
        public void setDeviceName(String deviceName) {
            this.deviceName = deviceName;
        }

        /**
         * Gets the device id.
         *
         * @return the device id
         */
        @XmlElement
        public long getDeviceId() {
            return deviceId;
        }

        /**
         * Sets the device id.
         *
         * @param deviceId the new device id
         */
        public void setDeviceId(long deviceId) {
            this.deviceId = deviceId;
        }

        /**
         * Gets the author.
         *
         * @return the author
         */
        @XmlElement
        public String getAuthor() {
            return author;
        }

        /**
         * Sets the author.
         *
         * @param author the new author
         */
        public void setAuthor(String author) {
            this.author = author;
        }

        /**
         * Gets the old change date.
         *
         * @return the old change date
         */
        @XmlElement
        public Date getOldChangeDate() {
            return oldChangeDate;
        }

        /**
         * Sets the old change date.
         *
         * @param oldChangeDate the new old change date
         */
        public void setOldChangeDate(Date oldChangeDate) {
            this.oldChangeDate = oldChangeDate;
        }

        /**
         * Gets the new change date.
         *
         * @return the new change date
         */
        @XmlElement
        public Date getNewChangeDate() {
            return newChangeDate;
        }

        /**
         * Sets the new change date.
         *
         * @param newChangeDate the new new change date
         */
        public void setNewChangeDate(Date newChangeDate) {
            this.newChangeDate = newChangeDate;
        }

        /**
         * Gets the old id.
         *
         * @return the old id
         */
        @XmlElement
        public long getOldId() {
            return oldId;
        }

        /**
         * Sets the old id.
         *
         * @param oldId the new old id
         */
        public void setOldId(long oldId) {
            this.oldId = oldId;
        }

        /**
         * Gets the new id.
         *
         * @return the new id
         */
        @XmlElement
        public long getNewId() {
            return newId;
        }

        /**
         * Sets the new id.
         *
         * @param newId the new new id
         */
        public void setNewId(long newId) {
            this.newId = newId;
        }
    }

    /**
     * The Class RsChangeCriteria.
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsChangeCriteria {

        /**
         * The from date.
         */
        private Date fromDate;

        /**
         * The to date.
         */
        private Date toDate;

        /**
         * Instantiates a new rs change criteria.
         */
        public RsChangeCriteria() {
            this.toDate = new Date();
            Calendar c = Calendar.getInstance();
            c.setTime(this.toDate);
            c.add(Calendar.DAY_OF_MONTH, -1);
            this.fromDate = c.getTime();
        }

        /**
         * Gets the from date.
         *
         * @return the from date
         */
        @XmlElement
        public Date getFromDate() {
            return fromDate;
        }

        /**
         * Sets the from date.
         *
         * @param fromDate the new from date
         */
        public void setFromDate(Date fromDate) {
            this.fromDate = fromDate;
        }

        /**
         * Gets the to date.
         *
         * @return the to date
         */
        @XmlElement
        public Date getToDate() {
            return toDate;
        }

        /**
         * Sets the to date.
         *
         * @param toDate the new to date
         */
        public void setToDate(Date toDate) {
            this.toDate = toDate;
        }
    }

    /**
     * The Class RsPolicy.
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsPolicy {

        /**
         * The id.
         */
        private long id = 0;

        /**
         * The name.
         */
        private String name = "";

        /**
         * The group.
         */
        private long group = 0;

        /**
         * Instantiates a new rs policy.
         */
        public RsPolicy() {

        }

        /**
         * Gets the id.
         *
         * @return the id
         */
        @XmlElement
        public long getId() {
            return id;
        }

        /**
         * Sets the id.
         *
         * @param id the new id
         */
        public void setId(long id) {
            this.id = id;
        }

        /**
         * Gets the name.
         *
         * @return the name
         */
        @XmlElement
        public String getName() {
            return name;
        }

        /**
         * Sets the name.
         *
         * @param name the new name
         */
        public void setName(String name) {
            this.name = name;
        }

        /**
         * Gets the group.
         *
         * @return the group
         */
        @XmlElement
        public long getGroup() {
            return group;
        }

        /**
         * Sets the group.
         *
         * @param group the new group
         */
        public void setGroup(long group) {
            this.group = group;
        }
    }

    /**
     * The Class RsRule.
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsRule {

        /**
         * The id.
         */
        private long id = 0;

        /**
         * The name.
         */
        private String name = null;

        /**
         * The type.
         */
        private String type = "";

        /**
         * The script.
         */
        private String script = null;

        /**
         * The policy.
         */
        private long policy = 0;

        /**
         * The enabled.
         */
        private boolean enabled = false;

        /**
         * The exemptions.
         */
        private Map<Long, Date> exemptions = new HashMap<Long, Date>();

        private String text = null;
        private Boolean regExp;
        private String context = null;
        private String driver = null;
        private String field = null;
        private Boolean anyBlock;
        private Boolean matchAll;
        private Boolean invert;

        /**
         * Gets the id.
         *
         * @return the id
         */
        @XmlElement
        public Long getId() {
            return id;
        }

        /**
         * Sets the id.
         *
         * @param id the new id
         */
        public void setId(long id) {
            this.id = id;
        }

        /**
         * Gets the name.
         *
         * @return the name
         */
        @XmlElement
        public String getName() {
            return name;
        }

        /**
         * Sets the name.
         *
         * @param name the new name
         */
        public void setName(String name) {
            this.name = name;
        }


        @XmlElement
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        /**
         * Gets the script.
         *
         * @return the script
         */
        @XmlElement
        public String getScript() {
            return script;
        }

        /**
         * Sets the script.
         *
         * @param script the new script
         */
        public void setScript(String script) {
            this.script = script;
        }

        /**
         * Gets the policy.
         *
         * @return the policy
         */
        @XmlElement
        public Long getPolicy() {
            return policy;
        }

        /**
         * Sets the policy.
         *
         * @param policy the new policy
         */
        public void setPolicy(long policy) {
            this.policy = policy;
        }

        /**
         * Checks if is enabled.
         *
         * @return true, if is enabled
         */
        @XmlElement
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets the enabled.
         *
         * @param enabled the new enabled
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Gets the exemptions.
         *
         * @return the exemptions
         */
        @XmlElement
        public Map<Long, Date> getExemptions() {
            return exemptions;
        }

        /**
         * Sets the exemptions.
         *
         * @param exemptions the exemptions
         */
        public void setExemptions(Map<Long, Date> exemptions) {
            this.exemptions = exemptions;
        }

        @XmlElement
        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        @XmlElement
        public Boolean isRegExp() {
            return regExp;
        }

        public void setRegExp(Boolean regExp) {
            this.regExp = regExp;
        }

        @XmlElement
        public String getContext() {
            return context;
        }

        public void setContext(String context) {
            this.context = context;
        }

        @XmlElement
        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        @XmlElement
        public String getDriver() {
            return driver;
        }

        public void setDriver(String driver) {
            this.driver = driver;
        }

        @XmlElement
        public Boolean isInvert() {
            return invert;
        }

        public void setInvert(Boolean invert) {
            this.invert = invert;
        }

        @XmlElement
        public Boolean isAnyBlock() {
            return anyBlock;
        }

        public void setAnyBlock(Boolean anyBlock) {
            this.anyBlock = anyBlock;
        }

        @XmlElement
        public Boolean isMatchAll() {
            return matchAll;
        }

        public void setMatchAll(Boolean matchAll) {
            this.matchAll = matchAll;
        }
    }

    /**
     * The Class RsJsRuleTest.
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsRuleTest extends RsRule {

        /**
         * The device.
         */
        private long device = 0;

        /**
         * Gets the device.
         *
         * @return the device
         */
        @XmlElement
        public long getDevice() {
            return device;
        }

        /**
         * Sets the device.
         *
         * @param device the new device
         */
        public void setDevice(long device) {
            this.device = device;
        }


    }

    /**
     * The Class RsRuleTestResult.
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsRuleTestResult {

        /**
         * The result.
         */
        private CheckResult.ResultOption result;

        /**
         * The script error.
         */
        private String scriptError;

        /**
         * Gets the result.
         *
         * @return the result
         */
        @XmlElement
        public CheckResult.ResultOption getResult() {
            return result;
        }

        /**
         * Sets the result.
         *
         * @param result the new result
         */
        public void setResult(CheckResult.ResultOption result) {
            this.result = result;
        }

        /**
         * Gets the script error.
         *
         * @return the script error
         */
        @XmlElement
        public String getScriptError() {
            return scriptError;
        }

        /**
         * Sets the script error.
         *
         * @param scriptError the new script error
         */
        public void setScriptError(String scriptError) {
            this.scriptError = scriptError;
        }

    }

    /**
     * The Class RsLightExemptedDevice.
     */
    @XmlRootElement
    @XmlAccessorType(value = XmlAccessType.NONE)
    public static class RsLightExemptedDevice extends RsLightDevice {

        /**
         * The expiration date.
         */
        private Date expirationDate;

        /**
         * Gets the expiration date.
         *
         * @return the expiration date
         */
        @XmlElement
        public Date getExpirationDate() {
            return expirationDate;
        }

        /**
         * Sets the expiration date.
         *
         * @param expirationDate the new expiration date
         */
        public void setExpirationDate(Date expirationDate) {
            this.expirationDate = expirationDate;
        }

    }

    /**
     * The Class RsDeviceRule.
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsDeviceRule {

        /**
         * The id.
         */
        private long id = 0;

        /**
         * The rule name.
         */
        private String ruleName = "";

        /**
         * The policy name.
         */
        private String policyName = "";

        /**
         * The result.
         */
        private CheckResult.ResultOption result;

        /**
         * The comment.
         */
        private String comment = "";

        /**
         * The check date.
         */
        private Date checkDate;

        /**
         * The expiration date.
         */
        private Date expirationDate;


        /**
         * Gets the id.
         *
         * @return the id
         */
        @XmlElement
        public Long getId() {
            return id;
        }

        /**
         * Sets the id.
         *
         * @param id the new id
         */
        public void setId(long id) {
            this.id = id;
        }

        /**
         * Gets the rule name.
         *
         * @return the rule name
         */
        @XmlElement
        public String getRuleName() {
            return ruleName;
        }

        /**
         * Sets the rule name.
         *
         * @param ruleName the new rule name
         */
        public void setRuleName(String ruleName) {
            this.ruleName = ruleName;
        }

        /**
         * Gets the policy name.
         *
         * @return the policy name
         */
        @XmlElement
        public String getPolicyName() {
            return policyName;
        }

        /**
         * Sets the policy name.
         *
         * @param policyName the new policy name
         */
        public void setPolicyName(String policyName) {
            this.policyName = policyName;
        }

        /**
         * Gets the result.
         *
         * @return the result
         */
        @XmlElement
        public CheckResult.ResultOption getResult() {
            return result;
        }

        /**
         * Sets the result.
         *
         * @param result the new result
         */
        public void setResult(CheckResult.ResultOption result) {
            this.result = result;
        }

        /**
         * Gets the check date.
         *
         * @return the check date
         */
        @XmlElement
        public Date getCheckDate() {
            return checkDate;
        }

        /**
         * Sets the check date.
         *
         * @param checkDate the new check date
         */
        public void setCheckDate(Date checkDate) {
            this.checkDate = checkDate;
        }

        /**
         * Gets the expiration date.
         *
         * @return the expiration date
         */
        @XmlElement
        public Date getExpirationDate() {
            return expirationDate;
        }

        /**
         * Sets the expiration date.
         *
         * @param expirationDate the new expiration date
         */
        public void setExpirationDate(Date expirationDate) {
            this.expirationDate = expirationDate;
        }

        /**
         * Gets the comment.
         *
         * @return the comment
         */
        @XmlElement
        public String getComment() {
            return comment;
        }

        /**
         * Sets the comment.
         *
         * @param comment the new comment
         */
        public void setComment(String comment) {
            this.comment = comment;
        }

    }

    /**
     * The Class RsConfigChangeNumberByDateStat.
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsConfigChangeNumberByDateStat {

        /**
         * The change count.
         */
        private long changeCount;

        /**
         * The change day.
         */
        private Date changeDay;

        /**
         * Gets the change count.
         *
         * @return the change count
         */
        @XmlElement
        public long getChangeCount() {
            return changeCount;
        }

        /**
         * Sets the change count.
         *
         * @param changes the new change count
         */
        public void setChangeCount(long changes) {
            this.changeCount = changes;
        }

        /**
         * Gets the change day.
         *
         * @return the change day
         */
        @XmlElement
        public Date getChangeDay() {
            return changeDay;
        }

        /**
         * Sets the change day.
         *
         * @param date the new change day
         */
        public void setChangeDay(Date date) {
            this.changeDay = date;
        }


    }

    /**
     * The Class RsGroupConfigComplianceStat.
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsGroupConfigComplianceStat {

        /**
         * The group id.
         */
        private long groupId;

        /**
         * The group name.
         */
        private String groupName;

        /**
         * The compliant device count.
         */
        private long compliantDeviceCount;

        /**
         * The device count.
         */
        private long deviceCount;

        /**
         * Gets the group id.
         *
         * @return the group id
         */
        @XmlElement
        public long getGroupId() {
            return groupId;
        }

        /**
         * Sets the group id.
         *
         * @param groupId the new group id
         */
        public void setGroupId(long groupId) {
            this.groupId = groupId;
        }

        /**
         * Gets the group name.
         *
         * @return the group name
         */
        @XmlElement
        public String getGroupName() {
            return groupName;
        }

        /**
         * Sets the group name.
         *
         * @param groupName the new group name
         */
        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }

        /**
         * Gets the compliant device count.
         *
         * @return the compliant device count
         */
        @XmlElement
        public long getCompliantDeviceCount() {
            return compliantDeviceCount;
        }

        /**
         * Sets the compliant device count.
         *
         * @param compliantCount the new compliant device count
         */
        public void setCompliantDeviceCount(long compliantCount) {
            this.compliantDeviceCount = compliantCount;
        }

        /**
         * Gets the device count.
         *
         * @return the device count
         */
        @XmlElement
        public long getDeviceCount() {
            return deviceCount;
        }

        /**
         * Sets the device count.
         *
         * @param deviceCount the new device count
         */
        public void setDeviceCount(long deviceCount) {
            this.deviceCount = deviceCount;
        }
    }

    /**
     * The Class RsGroupSoftwareComplianceStat.
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsGroupSoftwareComplianceStat {

        /**
         * The group id.
         */
        private long groupId;

        /**
         * The group name.
         */
        private String groupName;

        /**
         * The gold device count.
         */
        private long goldDeviceCount;

        /**
         * The silver device count.
         */
        private long silverDeviceCount;

        /**
         * The bronze device count.
         */
        private long bronzeDeviceCount;

        /**
         * The device count.
         */
        private long deviceCount;

        /**
         * Gets the group id.
         *
         * @return the group id
         */
        @XmlElement
        public long getGroupId() {
            return groupId;
        }

        /**
         * Sets the group id.
         *
         * @param groupId the new group id
         */
        public void setGroupId(long groupId) {
            this.groupId = groupId;
        }

        /**
         * Gets the group name.
         *
         * @return the group name
         */
        @XmlElement
        public String getGroupName() {
            return groupName;
        }

        /**
         * Sets the group name.
         *
         * @param groupName the new group name
         */
        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }

        /**
         * Gets the gold device count.
         *
         * @return the gold device count
         */
        @XmlElement
        public long getGoldDeviceCount() {
            return goldDeviceCount;
        }

        /**
         * Sets the gold device count.
         *
         * @param goldDeviceCount the new gold device count
         */
        public void setGoldDeviceCount(long goldDeviceCount) {
            this.goldDeviceCount = goldDeviceCount;
        }

        /**
         * Gets the silver device count.
         *
         * @return the silver device count
         */
        @XmlElement
        public long getSilverDeviceCount() {
            return silverDeviceCount;
        }

        /**
         * Sets the silver device count.
         *
         * @param silverDeviceCount the new silver device count
         */
        public void setSilverDeviceCount(long silverDeviceCount) {
            this.silverDeviceCount = silverDeviceCount;
        }

        /**
         * Gets the bronze device count.
         *
         * @return the bronze device count
         */
        @XmlElement
        public long getBronzeDeviceCount() {
            return bronzeDeviceCount;
        }

        /**
         * Sets the bronze device count.
         *
         * @param bronzeDeviceCount the new bronze device count
         */
        public void setBronzeDeviceCount(long bronzeDeviceCount) {
            this.bronzeDeviceCount = bronzeDeviceCount;
        }

        /**
         * Gets the device count.
         *
         * @return the device count
         */
        @XmlElement
        public long getDeviceCount() {
            return deviceCount;
        }

        /**
         * Sets the device count.
         *
         * @param deviceCount the new device count
         */
        public void setDeviceCount(long deviceCount) {
            this.deviceCount = deviceCount;
        }
    }

    /**
     * The Class RsLightPolicyRuleDevice.
     */
    @XmlRootElement
    @XmlAccessorType(value = XmlAccessType.NONE)
    public static class RsLightPolicyRuleDevice extends RsLightDevice {

        /**
         * The rule name.
         */
        private String ruleName;

        /**
         * The policy name.
         */
        private String policyName;

        /**
         * The check date.
         */
        private Date checkDate;

        /**
         * The result.
         */
        private CheckResult.ResultOption result;

        /**
         * Gets the rule name.
         *
         * @return the rule name
         */
        @XmlElement
        public String getRuleName() {
            return ruleName;
        }

        /**
         * Sets the rule name.
         *
         * @param ruleName the new rule name
         */
        public void setRuleName(String ruleName) {
            this.ruleName = ruleName;
        }

        /**
         * Gets the policy name.
         *
         * @return the policy name
         */
        @XmlElement
        public String getPolicyName() {
            return policyName;
        }

        /**
         * Sets the policy name.
         *
         * @param policyName the new policy name
         */
        public void setPolicyName(String policyName) {
            this.policyName = policyName;
        }

        /**
         * Gets the check date.
         *
         * @return the check date
         */
        @XmlElement
        public Date getCheckDate() {
            return checkDate;
        }

        /**
         * Sets the check date.
         *
         * @param checkDate the new check date
         */
        public void setCheckDate(Date checkDate) {
            this.checkDate = checkDate;
        }

        /**
         * Gets the result.
         *
         * @return the result
         */
        @XmlElement
        public CheckResult.ResultOption getResult() {
            return result;
        }

        /**
         * Sets the result.
         *
         * @param result the new result
         */
        public void setResult(CheckResult.ResultOption result) {
            this.result = result;
        }
    }

    /**
     * The Class RsHardwareRule.
     */
    @XmlRootElement
    @XmlAccessorType(value = XmlAccessType.NONE)
    public static class RsHardwareRule {

        /**
         * The id.
         */
        private long id;

        /**
         * The group.
         */
        private long group = -1;

        /**
         * The device class name.
         */
        private String driver = "";

        /**
         * The part number.
         */
        private String partNumber = "";

        private boolean partNumberRegExp = false;

        /**
         * The family.
         */
        private String family = "";

        private boolean familyRegExp = false;

        private Date endOfSale = null;

        private Date endOfLife = null;

        @XmlElement
        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        @XmlElement
        public long getGroup() {
            return group;
        }

        public void setGroup(long group) {
            this.group = group;
        }

        @XmlElement
        public String getDriver() {
            return driver;
        }

        public void setDriver(String driver) {
            this.driver = driver;
        }

        @XmlElement
        public String getPartNumber() {
            return partNumber;
        }

        public void setPartNumber(String partNumber) {
            this.partNumber = partNumber;
        }

        @XmlElement
        public boolean isPartNumberRegExp() {
            return partNumberRegExp;
        }

        public void setPartNumberRegExp(boolean partNumberRegExp) {
            this.partNumberRegExp = partNumberRegExp;
        }

        @XmlElement
        public String getFamily() {
            return family;
        }

        public void setFamily(String family) {
            this.family = family;
        }

        @XmlElement
        public boolean isFamilyRegExp() {
            return familyRegExp;
        }

        public void setFamilyRegExp(boolean familyRegExp) {
            this.familyRegExp = familyRegExp;
        }

        @XmlElement(nillable = true)
        public Date getEndOfSale() {
            return endOfSale;
        }

        public void setEndOfSale(Date endOfSale) {
            this.endOfSale = endOfSale;
        }

        @XmlElement(nillable = true)
        public Date getEndOfLife() {
            return endOfLife;
        }

        public void setEndOfLife(Date endOfLife) {
            this.endOfLife = endOfLife;
        }
    }

    /**
     * The Class RsSoftwareRule.
     */
    @XmlRootElement
    @XmlAccessorType(value = XmlAccessType.NONE)
    public static class RsSoftwareRule {

        /**
         * The id.
         */
        private long id;

        /**
         * The group.
         */
        private long group = -1;

        /**
         * The device class name.
         */
        private String driver = "";

        /**
         * The version.
         */
        private String version = "";

        private boolean versionRegExp = false;

        /**
         * The family.
         */
        private String family = "";

        private boolean familyRegExp = false;

        /**
         * The level.
         */
        private SoftwareRule.ConformanceLevel level = SoftwareRule.ConformanceLevel.GOLD;

        /**
         * The priority.
         */
        private double priority = -1;

        /**
         * Gets the id.
         *
         * @return the id
         */
        @XmlElement
        public long getId() {
            return id;
        }

        /**
         * Sets the id.
         *
         * @param id the new id
         */
        public void setId(long id) {
            this.id = id;
        }

        /**
         * Gets the group.
         *
         * @return the group
         */
        @XmlElement
        public long getGroup() {
            return group;
        }

        /**
         * Sets the group.
         *
         * @param group the new group
         */
        public void setGroup(long group) {
            this.group = group;
        }

        /**
         * Gets the device class name.
         *
         * @return the device class name
         */
        @XmlElement
        public String getDriver() {
            return driver;
        }

        public void setDriver(String driver) {
            this.driver = driver;
        }

        /**
         * Gets the version.
         *
         * @return the version
         */
        @XmlElement
        public String getVersion() {
            return version;
        }

        /**
         * Sets the version.
         *
         * @param version the new version
         */
        public void setVersion(String version) {
            this.version = version;
        }

        /**
         * Gets the family.
         *
         * @return the family
         */
        @XmlElement
        public String getFamily() {
            return family;
        }

        /**
         * Sets the family.
         *
         * @param family the new family
         */
        public void setFamily(String family) {
            this.family = family;
        }

        /**
         * Gets the level.
         *
         * @return the level
         */
        @XmlElement
        public SoftwareRule.ConformanceLevel getLevel() {
            return level;
        }

        /**
         * Sets the level.
         *
         * @param level the new level
         */
        public void setLevel(SoftwareRule.ConformanceLevel level) {
            this.level = level;
        }

        /**
         * Gets the priority.
         *
         * @return the priority
         */
        @XmlElement
        public double getPriority() {
            return priority;
        }

        /**
         * Sets the priority.
         *
         * @param priority the new priority
         */
        public void setPriority(double priority) {
            this.priority = priority;
        }

        @XmlElement
        public boolean isVersionRegExp() {
            return versionRegExp;
        }

        public void setVersionRegExp(boolean versionRegExp) {
            this.versionRegExp = versionRegExp;
        }

        @XmlElement
        public boolean isFamilyRegExp() {
            return familyRegExp;
        }

        public void setFamilyRegExp(boolean familyRegExp) {
            this.familyRegExp = familyRegExp;
        }
    }

    /**
     * The Class RsLightSoftwareLevelDevice.
     */
    @XmlRootElement
    @XmlAccessorType(value = XmlAccessType.NONE)
    public static class RsLightSoftwareLevelDevice extends RsLightDevice {

        /**
         * The software level.
         */
        private SoftwareRule.ConformanceLevel softwareLevel;

        /**
         * Gets the software level.
         *
         * @return the software level
         */
        @XmlElement
        public SoftwareRule.ConformanceLevel getSoftwareLevel() {
            return softwareLevel;
        }

        /**
         * Sets the software level.
         *
         * @param level the new software level
         */
        public void setSoftwareLevel(SoftwareRule.ConformanceLevel level) {
            this.softwareLevel = level;
        }
    }

    /**
     * The Class RsLightSoftwareLevelDevice.
     */
    @XmlRootElement
    @XmlAccessorType(value = XmlAccessType.NONE)
    public static class RsLightAccessFailureDevice extends RsLightDevice {

        private Date lastSuccess;

        private Date lastFailure;

        @XmlElement
        public Date getLastSuccess() {
            return lastSuccess;
        }

        public void setLastSuccess(Date lastSuccess) {
            this.lastSuccess = lastSuccess;
        }

        @XmlElement
        public Date getLastFailure() {
            return lastFailure;
        }

        public void setLastFailure(Date lastFailure) {
            this.lastFailure = lastFailure;
        }
    }

    /**
     * The Class RsLogin.
     */
    @XmlRootElement
    @XmlAccessorType(value = XmlAccessType.NONE)
    public static class RsLogin {

        /**
         * The username.
         */
        private String username;

        /**
         * The password.
         */
        private String password;

        /**
         * The new password.
         */
        private String newPassword = "";

        /**
         * Gets the username.
         *
         * @return the username
         */
        @XmlElement
        public String getUsername() {
            return username;
        }

        /**
         * Sets the username.
         *
         * @param username the new username
         */
        public void setUsername(String username) {
            this.username = username;
        }

        /**
         * Gets the password.
         *
         * @return the password
         */
        @XmlElement
        public String getPassword() {
            return password;
        }

        /**
         * Sets the password.
         *
         * @param password the new password
         */
        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * Gets the new password.
         *
         * @return the new password
         */
        @XmlElement
        public String getNewPassword() {
            return newPassword;
        }

        /**
         * Sets the new password.
         *
         * @param newPassword the new new password
         */
        public void setNewPassword(String newPassword) {
            this.newPassword = newPassword;
        }
    }

    /**
     * The Class RsUser.
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsUser {

        /**
         * The id.
         */
        private long id;

        /**
         * The username.
         */
        private String username;

        /**
         * The password.
         */
        private String password;

        /**
         * The level.
         */
        private int level;

        /**
         * The local.
         */
        private boolean local;

        /**
         * Gets the id.
         *
         * @return the id
         */
        @XmlElement
        public long getId() {
            return id;
        }

        /**
         * Sets the id.
         *
         * @param id the new id
         */
        public void setId(long id) {
            this.id = id;
        }

        /**
         * Gets the username.
         *
         * @return the username
         */
        @XmlElement
        public String getUsername() {
            return username;
        }

        /**
         * Sets the username.
         *
         * @param username the new username
         */
        public void setUsername(String username) {
            this.username = username;
        }

        /**
         * Gets the password.
         *
         * @return the password
         */
        @XmlElement
        public String getPassword() {
            return password;
        }

        /**
         * Sets the password.
         *
         * @param password the new password
         */
        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * Gets the level.
         *
         * @return the level
         */
        @XmlElement
        public int getLevel() {
            return level;
        }

        /**
         * Sets the level.
         *
         * @param level the new level
         */
        public void setLevel(int level) {
            this.level = level;
        }

        /**
         * Checks if is local.
         *
         * @return true, if is local
         */
        @XmlElement
        public boolean isLocal() {
            return local;
        }

        /**
         * Sets the local.
         *
         * @param local the new local
         */
        public void setLocal(boolean local) {
            this.local = local;
        }
    }

    /**
     * The Class RsNewDeviceVirtual.
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsNewDeviceVirtual {


        /**
         * The name.
         */
        private Integer type;

        /**
         * The company.
         */
        private long company_id;

        /**
         * The folder.
         */
        private String folder;

        /**
         * The name.
         */
        private String name;

        private String task;

        private String hour;

        private String date;


        @XmlElement
        public Integer getType() {
            return type;
        }

        public void setType(Integer type) {
            this.type = type;
        }

        @XmlElement
        public long getCompany() {
            return company_id;
        }

        public void setCompany(long company) {
            this.company_id = company;
        }

        @XmlElement
        public String getFolder() {
            return folder;
        }

        public void setFolder(String folder) {
            this.folder = folder;
        }

        @XmlElement
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @XmlElement
        public String getTask() {
            return task;
        }

        public void setTask(String task) {
            this.task = task;
        }

        @XmlElement
        public String getHour() {
            return hour;
        }

        public void setHour(String hour) {
            this.hour = hour;
        }

        @XmlElement
        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }
    }

    /**
     * The Class RsNewDeviceVirtual.
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsNewCompany {


        /**
         * The name.
         */
        private String name;


        @XmlElement
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

    }

    /**
     * The Class RsNewuserSsh.
     */
    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsNewuserSsh {


        /**
         * The name.
         */
        private String name;
        private String password;
        private String certificat;


        @XmlElement
        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @XmlElement
        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        @XmlElement
        public String getCertificat() {
            return certificat;
        }

        public void setCertificat(String certificat) {
            this.certificat = certificat;
        }
    }


    /********************************************************************************************************************/

    @Priority(Priorities.AUTHORIZATION)
    @PreMatching
    private static class SecurityFilter implements ContainerRequestFilter {

        @Inject
        javax.inject.Provider<UriInfo> uriInfo;
        @Context
        private HttpServletRequest httpRequest;

        @Override
        public void filter(ContainerRequestContext requestContext) throws IOException {
            User user = (User) httpRequest.getSession().getAttribute("user");
            requestContext.setSecurityContext(new Authorizer(user));
        }

        private class Authorizer implements SecurityContext {

            private User user;

            public Authorizer(User user) {
                this.user = user;
            }

            @Override
            public boolean isUserInRole(String role) {
                return (user != null &&
                        (("admin".equals(role) && user.getLevel() >= User.LEVEL_ADMIN) ||
                                ("readwrite".equals(role) && user.getLevel() >= User.LEVEL_READWRITE) ||
                                ("readonly".equals(role) && user.getLevel() >= User.LEVEL_READONLY)));
            }

            @Override
            public boolean isSecure() {
                return "https".equals(uriInfo.get().getRequestUri().getScheme());
            }

            @Override
            public Principal getUserPrincipal() {
                return user;
            }

            @Override
            public String getAuthenticationScheme() {
                return SecurityContext.FORM_AUTH;
            }
        }

    }

    public static class NetshotExceptionMapper implements ExceptionMapper<Throwable> {

        public Response toResponse(Throwable t) {
            if (!(t instanceof ForbiddenException)) {
                logger.error("Uncaught exception thrown by REST service", t);
            }
            if (t instanceof WebApplicationException) {
                return ((WebApplicationException) t).getResponse();
            } else {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .build();
            }
        }
    }

    public static class NetshotWebApplication extends ResourceConfig {
        public NetshotWebApplication() {
            registerClasses(RestService.class, SecurityFilter.class);
            register(NetshotExceptionMapper.class);
            register(RolesAllowedDynamicFeature.class);
            property(ServerProperties.RESPONSE_SET_STATUS_OVER_SEND_ERROR, "true");
            property(ServerProperties.APPLICATION_NAME, "Plumber");
            //property(ServerProperties.TRACING, "ALL");
            register(JacksonFeature.class);
        }
    }

    /**
     * An error bean to be sent to the REST client.
     */
    @XmlRootElement(name = "error")
    @XmlAccessorType(XmlAccessType.NONE)
    public static class RsErrorBean {

        /**
         * The error message.
         */
        private String errorMsg;

        /**
         * The error code.
         */
        private int errorCode;

        /**
         * Instantiates a new error bean.
         */
        public RsErrorBean() {
        }

        /**
         * Instantiates a new error bean.
         *
         * @param errorMsg  the error msg
         * @param errorCode the error code
         */
        public RsErrorBean(String errorMsg, int errorCode) {
            super();
            this.errorMsg = errorMsg;
            this.errorCode = errorCode;
        }

        /**
         * Gets the error message.msg
         *
         * @return the error message
         */
        @XmlElement
        public String getErrorMsg() {
            return errorMsg;
        }

        /**
         * Sets the error message.
         *
         * @param errorMsg the new error message
         */
        public void setErrorMsg(String errorMsg) {
            this.errorMsg = errorMsg;
        }

        /**
         * Gets the error code.
         *
         * @return the error code
         */
        @XmlElement
        public int getErrorCode() {
            return errorCode;
        }

        /**
         * Sets the error code.
         *
         * @param errorCode the new error code
         */
        public void setErrorCode(int errorCode) {
            this.errorCode = errorCode;
        }
    }

    /**
     * The NetshotBadRequestException class, a WebApplication exception
     * embedding an error message, to be sent to the REST client.
     */
    static public class NetshotBadRequestException extends WebApplicationException {

        /**
         * The Constant NETSHOT_DATABASE_ACCESS_ERROR.
         */
        public static final int NETSHOT_DATABASE_ACCESS_ERROR = 20;

        /**
         * The Constant NETSHOT_INVALID_IP_ADDRESS.
         */
        public static final int NETSHOT_INVALID_IP_ADDRESS = 100;

        /**
         * The Constant NETSHOT_MALFORMED_IP_ADDRESS.
         */
        public static final int NETSHOT_MALFORMED_IP_ADDRESS = 101;

        /**
         * The Constant NETSHOT_INVALID_DOMAIN.
         */
        public static final int NETSHOT_INVALID_DOMAIN = 110;

        /**
         * The Constant NETSHOT_DUPLICATE_DOMAIN.
         */
        public static final int NETSHOT_DUPLICATE_DOMAIN = 111;

        /**
         * The Constant NETSHOT_INVALID_DOMAIN_NAME.
         */
        public static final int NETSHOT_INVALID_DOMAIN_NAME = 112;

        /**
         * The Constant NETSHOT_USED_DOMAIN.
         */
        public static final int NETSHOT_USED_DOMAIN = 113;

        /**
         * The Constant NETSHOT_INVALID_TASK.
         */
        public static final int NETSHOT_INVALID_TASK = 120;

        /**
         * The Constant NETSHOT_TASK_NOT_CANCELLABLE.
         */
        public static final int NETSHOT_TASK_NOT_CANCELLABLE = 121;

        /**
         * The Constant NETSHOT_TASK_CANCEL_ERROR.
         */
        public static final int NETSHOT_TASK_CANCEL_ERROR = 122;

        /**
         * The Constant NETSHOT_USED_CREDENTIALS.
         */
        public static final int NETSHOT_USED_CREDENTIALS = 130;

        /**
         * The Constant NETSHOT_DUPLICATE_CREDENTIALS.
         */
        public static final int NETSHOT_DUPLICATE_CREDENTIALS = 131;

        /**
         * The Constant NETSHOT_INVALID_CREDENTIALS_TYPE.
         */
        public static final int NETSHOT_INVALID_CREDENTIALS_TYPE = 132;

        /**
         * The Constant NETSHOT_CREDENTIALS_NOTFOUND.
         */
        public static final int NETSHOT_CREDENTIALS_NOTFOUND = 133;

        /**
         * The Constant NETSHOT_SCHEDULE_ERROR.
         */
        public static final int NETSHOT_SCHEDULE_ERROR = 30;

        /**
         * The Constant NETSHOT_DUPLICATE_DEVICE.
         */
        public static final int NETSHOT_DUPLICATE_DEVICE = 140;

        /**
         * The Constant NETSHOT_USED_DEVICE.
         */
        public static final int NETSHOT_USED_DEVICE = 141;

        /**
         * The Constant NETSHOT_INVALID_DEVICE.
         */
        public static final int NETSHOT_INVALID_DEVICE = 142;

        /**
         * The Constant NETSHOT_INVALID_CONFIG.
         */
        public static final int NETSHOT_INVALID_CONFIG = 143;

        /**
         * The Constant NETSHOT_INCOMPATIBLE_CONFIGS.
         */
        public static final int NETSHOT_INCOMPATIBLE_CONFIGS = 144;

        /**
         * The Constant NETSHOT_INVALID_DEVICE_CLASSNAME.
         */
        public static final int NETSHOT_INVALID_DEVICE_CLASSNAME = 150;

        /**
         * The Constant NETSHOT_INVALID_SEARCH_STRING.
         */
        public static final int NETSHOT_INVALID_SEARCH_STRING = 151;

        /**
         * The Constant NETSHOT_INVALID_GROUP_NAME.
         */
        public static final int NETSHOT_INVALID_GROUP_NAME = 160;

        /**
         * The Constant NETSHOT_DUPLICATE_GROUP.
         */
        public static final int NETSHOT_DUPLICATE_GROUP = 161;

        /**
         * The Constant NETSHOT_INCOMPATIBLE_GROUP_TYPE.
         */
        public static final int NETSHOT_INCOMPATIBLE_GROUP_TYPE = 162;

        /**
         * The Constant NETSHOT_INVALID_DEVICE_IN_STATICGROUP.
         */
        public static final int NETSHOT_INVALID_DEVICE_IN_STATICGROUP = 163;

        /**
         * The Constant NETSHOT_INVALID_GROUP.
         */
        public static final int NETSHOT_INVALID_GROUP = 164;

        /**
         * The Constant NETSHOT_INVALID_DYNAMICGROUP_QUERY.
         */
        public static final int NETSHOT_INVALID_DYNAMICGROUP_QUERY = 165;

        /**
         * The Constant NETSHOT_INVALID_SUBNET.
         */
        public static final int NETSHOT_INVALID_SUBNET = 170;

        /**
         * The Constant NETSHOT_SCAN_SUBNET_TOO_BIG.
         */
        public static final int NETSHOT_SCAN_SUBNET_TOO_BIG = 171;

        /**
         * The Constant NETSHOT_INVALID_POLICY_NAME.
         */
        public static final int NETSHOT_INVALID_POLICY_NAME = 180;

        /**
         * The Constant NETSHOT_INVALID_POLICY.
         */
        public static final int NETSHOT_INVALID_POLICY = 181;

        /**
         * The Constant NETSHOT_DUPLICATE_POLICY.
         */
        public static final int NETSHOT_DUPLICATE_POLICY = 182;

        /**
         * The Constant NETSHOT_INVALID_RULE_NAME.
         */
        public static final int NETSHOT_INVALID_RULE_NAME = 190;

        /**
         * The Constant NETSHOT_INVALID_RULE.
         */
        public static final int NETSHOT_INVALID_RULE = 191;

        /**
         * The Constant NETSHOT_DUPLICATE_RULE.
         */
        public static final int NETSHOT_DUPLICATE_RULE = 192;

        /**
         * The Constant NETSHOT_INVALID_USER.
         */
        public static final int NETSHOT_INVALID_USER = 200;

        /**
         * The Constant NETSHOT_DUPLICATE_USER.
         */
        public static final int NETSHOT_DUPLICATE_USER = 201;

        /**
         * The Constant NETSHOT_INVALID_USER_NAME.
         */
        public static final int NETSHOT_INVALID_USER_NAME = 202;

        /**
         * The Constant NETSHOT_INVALID_PASSWORD.
         */
        public static final int NETSHOT_INVALID_PASSWORD = 203;

        public static final int NETSHOT_INVALID_SCRIPT = 220;

        public static final int NETSHOT_UNKNOWN_SCRIPT = 221;

        public static final int NETSHOT_DUPLICATE_SCRIPT = 222;

        /**
         * The Constant NETSHOT_ERROR_CREATEFOLDER
         */
        public static final int NETSHOT_ERROR_CREATEFOLDER = 301;

        /**
         * The Constant serialVersionUID.
         */
        private static final long serialVersionUID = -4538169756895835186L;

        /**
         * Instantiates a new netshot bad request exception.
         *
         * @param message   the message
         * @param errorCode the error code
         */
        public NetshotBadRequestException(String message, int errorCode) {
            super(Response.status(Response.Status.BAD_REQUEST)
                    .entity(new RsErrorBean(message, errorCode)).build());
        }
    }

    static public class NetshotNotAuthorizedException extends WebApplicationException {

        /**
         * The Constant serialVersionUID.
         */
        private static final long serialVersionUID = -453816975689585686L;

        public NetshotNotAuthorizedException(String message, int errorCode) {
            super(Response.status(Response.Status.FORBIDDEN)
                    .entity(new RsErrorBean(message, errorCode)).build());
        }
    }


    @XmlRootElement
    @XmlAccessorType(XmlAccessType.NONE)
    @JsonTypeInfo(use = JsonTypeInfo.Id.MINIMAL_CLASS, include = JsonTypeInfo.As.PROPERTY, property = "type")
    public abstract static class RsHardwareSupportStat {
        private Date eoxDate;
        private long deviceCount;

        @XmlElement
        public Date getEoxDate() {
            return eoxDate;
        }

        public void setEoxDate(Date date) {
            this.eoxDate = date;
        }

        @XmlElement
        public long getDeviceCount() {
            return deviceCount;
        }

        public void setDeviceCount(long deviceCount) {
            this.deviceCount = deviceCount;
        }

    }

    @XmlType
    public static class RsHardwareSupportEoSStat extends RsHardwareSupportStat {

    }

    @XmlType
    public static class RsHardwareSupportEoLStat extends RsHardwareSupportStat {

    }
}
