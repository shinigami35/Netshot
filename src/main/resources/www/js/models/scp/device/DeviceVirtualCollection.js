/** Copyright 2013-2014 NetFishers */
define([
	'underscore',
	'backbone',
    'models/scp/device/DeviceVirtualModel'
], function(_, Backbone, DeviceVirtualModel) {

	return Backbone.Collection.extend({

		url: "api/scp/device",
        model: DeviceVirtualModel

	});

});
