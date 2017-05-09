/**
 * Copyright 2013-2016 Sylvain Cadilhac (NetFishers)
 *
 * This file is part of Netshot.
 *
 * Netshot is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Netshot is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Netshot.  If not, see <http://www.gnu.org/licenses/>.
 */

var Info = {
    name: "Riverbed",
    description: "RiverbedOS",
    author: "Adrien GANDARIAS",
    version: "0.1"
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
            },
            configure: {
                cmd: "enable",
                options: ["enable", "disable"],
                target: "configure"
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
        macros: {
            configure: {
                cmd: "configure terminal",
                options: ["enable", "configure"],
                target: "configure"
            }
        }

    }
};

function snapshot(cli, device, config, debug) {

    cli.macro("enable");
    cli.command("enable");


    //var configuration = cli.command("show configuration");
    //config.set('configuration', configuration);

    var status = cli.command("show version");
    var hostname = status.match(/^Product name:[ ]*[A-Za-z0-9_\-.]+$/);
    if (hostname) {
        hostname = hostname[1];
        device.set("name", hostname);
    }


    var version = status.match(/^Product release:[ ]*[A-Za-z0-9.]+$/);
    version = (version ? version[1] : "Unknown");
    device.set("softwareVersion", version);
    config.set("osVersion", version);

    // var tmpfamily = status.match(/family: (.*)/);
    // var family = (tmpfamily ? tmpfamily[1] : "PanOS device");
    // device.set("family", family);
    // device.set("softwareVersion", version);
    // config.set("osVersion", version);
    //
    // device.set("networkClass", "FIREWALL");

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
