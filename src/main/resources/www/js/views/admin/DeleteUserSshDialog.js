/** Copyright 2013-2014 NetFishers */
define([
	'jquery',
	'underscore',
	'backbone',
	'views/Dialog',
	'models/user/UserSshModel',
	'text!templates/admin/deleteUserSsh.html'
], function($, _, Backbone, Dialog, UserSshModel, deleteUserTemplate) {

	return Dialog.extend({

		template: _.template(deleteUserTemplate),

		dialogOptions: {
			title: "Delete user SSH",
		},

		buttons: {
			"Confirm": function(event) {
				console.log(this.model)
				var that = this;
				var $button = $(event.target).closest("button");
				$button.button('disable');
				var saveModel = this.model.clone();
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
		}

	});
});
