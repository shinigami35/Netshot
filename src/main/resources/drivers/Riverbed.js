var Info = {
    name: "Riverbed",
    description: "RiverbedOS",
    author: "Adrien GANDARIAS",
    version: "0.3"
};

var Config = {
    "riverbedVersion": {
        type: "Text",
        title: "Riverbed version",
        comparable: true,
        searchable: true,
        checkable: true,
        dump: {
            pre: "## RiverbedOS version: ",
            preLine: "##  "
        }
    },
    "runningConfig": {
        type: "LongText",
        title: "Running configuration",
        comparable: true,
        searchable: true,
        checkable: true,
        dump: {
            pre: "!! Running configuration (taken on %when%):",
            post: "!! End of running configuration"
        }
    }
};

var Device = {
    "status": {
        type: "Text",
        title: "Status",
        searchable: true
    },
    "manageByCMC": {
        type: "Text",
        title: "Managed by CMC",
        searchable: true
    },
    "usedMemory": {
        type: "Text",
        title: "Used memory size (MB)",
        searchable: true
    },
    "freeMemory": {
        type: "Text",
        title: "Free memory size (MB)",
        searchable: true
    },
    "totalMemory": {
        type: "Text",
        title: "Total memory size (MB)",
        searchable: true
    }
};

var CLI = {
    telnet: {
        macros: {
            enable: {
                options: ["username", "password", "enable", "disable"],
                target: "enable"
            },
            configure: {
                options: ["username", "password", "enable", "disable"],
                target: "configure"
            }
        }
    },
    ssh: {
        macros: {
            enable: {
                options: ["enable", "disable"],
                target: "enable"
            },
            configure: {
                options: ["enable", "disable"],
                target: "configure"
            }
        }
    },
    username: {
        prompt: /^login as: $/,
        macros: {
            auto: {
                cmd: "$$NetshotUsername$$",
                options: ["password", "usernameAgain"]
            }
        }
    },
    password: {
        prompt: /^[A-Za-z0-9_:]+@([0-9]{1,3}.){3}.([0-9]{1,3})'s password:[ ]*$/,
        macros: {
            auto: {
                cmd: "$$NetshotPassword$$",
                options: ["usernameAgain", "disable", "enable"]
            }
        }
    },
    usernameAgain: {
        prompt: /^login as: $/,
        fail: "Authentication failed - Telnet authentication failure."
    },
    disable: {
        prompt: /^[A-Za-z\-_0-9.\/]+ (\(config\)[ ]*)?([>#])[ ]*$/,
        pager: {
            avoid: "terminal length 0",
            match: /^Lines [0-9]+-[0-9]+\s+$/,
            response: " "
        },
        macros: {
            enable: {
                cmd: "enable",
                options: ["enable", "disable"],
                target: "enable"
            }
        }
    },
    enable: {
        prompt: /^[A-Za-z\-_0-9.\/]+ (\(config\)[ ]*)?([>#])[ ]*$/,
        error: /^% (.*)/m,
        pager: {
            avoid: "terminal length 0",
            match: /^Lines [0-9]+-[0-9]+\s+$/,
            response: " "
        },
        macros: {}

    }
};

function snapshot(cli, device, config, debug) {

    cli.macro("enable");
    cli.command("enable");

    var configuration = cli.command("show configuration running");
    config.set('runningConfig', configuration);

    var info = cli.command("show info");
    var status_riverbed = info.match(/Status: (.*)/);
    if (status_riverbed) {
        status_riverbed = status_riverbed[1];
        device.set("status", status_riverbed);
    }
    var serial = info.match(/Serial: (.*)/);
    device.set("serialNumber", serial[1]);

    var cmc = info.match(/Managed by CMC: (.*)/);
    if (cmc && (cmc[1].match(/^[ ]*yes[ ]*$/) || cmc[1].match(/^[ ]*no[ ]*$/)))
        device.set("manageByCMC", cmc[1].replace(/[ ]*/, ""))

    var status = cli.command("show version");
    var hostname = status.match(/Product name: (.*)/);
    if (hostname) {
        hostname = hostname[1];
        device.set("name", hostname.replace(/\s/g, ""));
    }
    var version = status.match(/Product release:[\s]*([0-9]+.*)/m);
    version = (version ? version[1] : "Unknown");
    device.set("softwareVersion", version);
    config.set("osVersion", version);
    var revision = info.match(/Revision: (.*)/);
    device.set('softwareVersion', version + (revision ? " Revision " + revision[1] : ""))

    var tmpfamily = status.match(/Product model: (.*)/);
    var family = (tmpfamily ? tmpfamily[1] : "RIVOS device");
    device.set("family", family);
    device.set("softwareVersion", version);
    config.set("osVersion", version);
    device.set("networkClass", "UNKNOWN");

    var data = status.match(/System memory: (.*)/);
    if (data) {
        var dataTmp = data[1];
        var arrayData = dataTmp.split('/');
        for (var i = 0; i < arrayData.length; i++) {
            arrayData[i] = arrayData[i].replace(/\s\s/g, " ")
        }
        var used = arrayData[0].match(/[0-9]+/);
        var free = arrayData[1].match(/[0-9]+/);
        var total = arrayData[2].match(/[0-9]+/);
        device.set("usedMemory", used[0]);
        device.set("freeMemory", free[0]);
        device.set("totalMemory", total[0]);
    }


}

// No known log message upon configuration change

function analyzeTrap(trap, debug) {
    return trap["1.3.6.1.6.3.1.1.4.1.0"] == "1.3.6.1.4.1.12356.101.6.0.1003" ||
        trap["1.3.6.1.6.3.1.1.4.1.0"] == "1.3.6.1.2.1.47.2.0.1";
}

function snmpAutoDiscover(sysObjectID, sysDesc) {
    return (sysObjectID.substring(0, 22) == "1.3.6.1.4.1.25461.2.3.") &&
        sysDesc.match(/^Palo Alto Networks PA-([0-9]+|VM) series firewall$/);
}
