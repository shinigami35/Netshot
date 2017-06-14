/** Copyright 2013-2014 NetFishers */
define([
    'underscore',
    'backbone',
], function (_, Backbone) {

    return Backbone.Model.extend({

        urlRoot: "api/scp/device",

        constructor: function (attributes, options) {
            Backbone.Model.apply(this, [
                attributes,
                _.omit(options, 'url')
            ]);
        },

        save: function (attrs, options) {
            attrs = attrs || this.toJSON();
            options = options || {};
            attrs = _.pick(attrs, [
                'type',
                'name',
                'company',
                'folder',
                'task',
                'hour',
                'date'
            ]);
            options.attrs = attrs;
            return Backbone.Model.prototype.save.call(this, attrs, options);
        }

    });

});