/** Copyright 2013-2014 NetFishers */
define([
	'underscore',
	'backbone',
	'models/user/UserSshModel'
], function(_, Backbone, UserSshModel) {

	return Backbone.Collection.extend({

		url: "api/users/ssh",
		model: UserSshModel
	});
});
