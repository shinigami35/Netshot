var Info = {
    name: "Palo Alto Panorama PAN-OS",
    description: "Panorama (PAN-OS)",
    author: "Adrien GANDARIAS",
    version: "0.3"
};

var Config = {
    "panOsVersion": {
        type: "Text",
        title: "PAN-OS version",
        comparable: true,
        searchable: true
    },
    "configuration": {
        type: "LongText",
        title: "Configuration",
        comparable: true,
        searchable: true,
        checkable: true,
        dump: {
            pre: "!! Configuration (taken on %when%):",
            post: "!! End of configuration"
        }
    }
};

var Device = {};

var CLI = {
    telnet: {
        macros: {
            operational: {
                options: ["username", "password", "operational"],
                target: "operational"
            }
        }
    },
    ssh: {
        macros: {
            operational: {
                options: ["operational"],
                target: "operational"
            }
        }
    },
    username: {
        prompt: /^login: $/,
        macros: {
            auto: {
                cmd: "$$NetshotUsername$$",
                options: ["password", "usernameAgain"]
            }
        }
    },
    password: {
        prompt: /^Password: $/,
        macros: {
            auto: {
                cmd: "$$NetshotPassword$$",
                options: ["usernameAgain", "operational"]
            }
        }
    },
    usernameAgain: {
        prompt: /^login: $/,
        fail: "Authentication failed - Telnet authentication failure."
    },
    operational: {
        prompt: /^([A-Za-z\-_0-9.]+@[a-zA-Z0-9._-]+(\((.*)*\))*> )$/,
        error: /^(Unknown command: .*|Invalid syntax.)/m,
        pager: {
            avoid: "set cli pager off",
            match: /lines [0-9]+-[0-9]+$/,
            response: " "
        },
        macros: {}
    }
};


function snapshot(cli, device, config) {



    var configCleanup = function (config) {
        var p = config.search(/^[a-z]/m);
        if (p > 0) {
            config = config.slice(p);
        }
        return config;
    };

    cli.macro("operational");

    var configuration = cli.command("show config running");
    config.set("configuration", configCleanup(configuration));

    var status = cli.command("show system info");
    var hostname = status.match(/hostname: (.*)$/m);
    if (hostname) {
        hostname = hostname[1];
        device.set("name", hostname);
    }

    var version = status.match(/sw-version: ([0-9]+.*)/);
    var tmpfamily = status.match(/family: (.*)/);
    var family = (tmpfamily ? tmpfamily[1] : "PanOS device");
    device.set("family", family);
    version = (version ? version[1] : "Unknown");
    device.set("softwareVersion", version);
    config.set("osVersion", version);

    device.set("networkClass", "FIREWALL");


    var serial = status.match(/serial: (.*)/);
    if (serial) {
        var module = {
            slot: (family === "vm"? "VM" : "Chassis"),
            partNumber: family,
            serialNumber: serial[1]
        };
        device.add("module", module);
        device.set("serialNumber", serial[1]);

    }
    else {
        device.set("serialNumber", "");
    }

    var interfaces = cli.command("show interface management");

    var ipName = interfaces.match(/Name: (.*)/);
    var ipInterface = interfaces.match(/Ip address: (.*)/);
    var ipInterfaceNetmask = interfaces.match(/Netmask: (.*)/);
    var ipmac = interfaces.match(/Port MAC address (.*)/);

    var networkInterface = {
        name: ipName[1],
        ip: [{
            ip: ipInterface[1].replace(/\s+/g, ''),
            mask: ipInterfaceNetmask[1].replace(/\s+/g, ''),
            usage: "PRIMARY"
        }],
        virtualDevice: "",
        mac: ipmac[1].replace(/\s+/g, '')
    };
    device.add("networkInterface", networkInterface);
}

function analyzeSyslog(message) {
    return !!message.match(/Commit job succeeded for user (.*)/);

}

function analyzeTrap(trap, debug) {
    var logOid = "1.3.6.1.4.1.25461.2.1.3.1.304";
    return typeof trap[logOid] === "string" && trap[logOid].match(/Commit job succeeded for user (.*)/);

}


function snmpAutoDiscover(sysObjectID, sysDesc) {
    return sysObjectID.substring(0, 22) === "1.3.6.1.4.1.25461.2.3."
        && sysDesc.match(/^Palo Alto.*/);

}