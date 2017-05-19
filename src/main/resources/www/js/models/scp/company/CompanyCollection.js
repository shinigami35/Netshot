/** Copyright 2013-2014 NetFishers */
define([
	'underscore',
	'backbone',
    'models/scp/company/CompanyModel'
], function(_, Backbone, CompanyModel) {

	return Backbone.Collection.extend({

		url: "api/scp/company",
        model: CompanyModel

	});

});
