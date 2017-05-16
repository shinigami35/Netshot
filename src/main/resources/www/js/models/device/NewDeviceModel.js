/** Copyright 2013-2014 NetFishers */
define([
    'underscore',
    'backbone',
], function (_, Backbone) {

    return Backbone.Model.extend({

        url: "api/devices",

        defaults: {
            'name': "No name",
            'ipAddress': "",
            'pathConfiguration': "/",
            'autoDiscover': true,
            'deviceType': "",
            'domainId': -1,
            'autoDiscoveryTask': -1,
            'retention': null,
            'emails': [],
            'onSuccess': true,
            'onError': true
        }

    });

});
