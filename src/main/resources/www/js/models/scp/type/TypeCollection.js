/** Copyright 2013-2014 NetFishers */
define([
	'underscore',
	'backbone',
    'models/scp/type/TypeModel'
], function(_, Backbone, TypeModel) {

	return Backbone.Collection.extend({

		url: "api/scp/type",
        model: TypeModel

	});

});
