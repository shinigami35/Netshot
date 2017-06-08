/** Copyright 2013-2014 NetFishers */
define([
	'jquery',
	'underscore',
	'backbone',
	'views/Dialog',
	'models/scp/device/DeviceVirtualModel',
	'text!templates/scp/deleteVirtualDevice.html'
], function($, _, Backbone, Dialog, DeviceVirtualModel, deleteVirtualDeviceTemplate) {

	return Dialog.extend({

		template: _.template(deleteVirtualDeviceTemplate),

		dialogOptions: {
			title: "Delete Virtual device",
		},

		buttons: {
			"Confirm": function(event) {
				var that = this;
				var $button = $(event.target).closest("button");
				$button.button('disable');
				var saveModel = that.model.clone();
				saveModel.destroy().done(function(data) {
					that.close();
					that.options.onDeleted();
				}).fail(function(data) {
					var error = $.parseJSON(data.responseText);
					that.$("#errormsg").text("Error: " + error.errorMsg);
					that.$("#error").show();
					$button.button('enable');
				});
			},
			"Cancel": function() {
				this.close();
			}
		},

	});
});
