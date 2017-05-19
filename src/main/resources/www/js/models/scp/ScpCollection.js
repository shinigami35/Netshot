/** Copyright 2013-2014 NetFishers */
define([
    'underscore',
    'backbone',
    'models/scp/ScpModel'
], function (_, Backbone, ScpModel) {

    return Backbone.Collection.extend({

        url: "api/scp/virtual",
        model: ScpModel
    });

});