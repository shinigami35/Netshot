/** Copyright 2013-2014 NetFishers */
define([
    'jquery',
    'underscore',
    'backbone',
    'text!templates/scp/virtualdevice.html',
    'text!templates/scp/tableau/scpLineTableau.html',
    'views/scp/dialog/DeleteVirtualDeviceDialog'
], function ($, _, Backbone, virtualTemplate, ScpLineTableau, DeleteVirtualDeviceDialog) {

    var DeviceVirtualView = Backbone.View.extend({

        el: "#nsdevices-device",

        template: _.template(virtualTemplate),
        lineTableauTemplate: _.template(ScpLineTableau),

        currentView: null,

        initialize: function (options) {
            var that = this;
        },

        refresh: function () {
            var that = this;
            this.model.fetch().done(function () {
                that.render();
            });
        },

        render: function () {
            var that = this;

            this.$el.html(this.template(this.model.toJSON()));

            var device = this.model.toJSON();
            that.line = "";
            device.file.forEach(function (elt) {
                that.line += that.lineTableauTemplate(elt);
            });
            if (that.line !== "") {
                that.$("#general>tbody").html("");
                that.$("#general>tbody").html(that.line);
            } else {
                that.$("#general>tbody").html("");
            }

            this.renderButton();

            Backbone.history.navigate("/scp/" + this.model.get('id'));

            return this;
        },
        renderButton: function () {
            var that = this;
             this.$('#refresh').button({
                 icons: {
                     primary: "ui-icon-refresh"
                 },
                 text: false
             }).click(function (e) {
                e.preventDefault();
                that.refresh();
             });
            // this.$('#edit').button({
            //     icons: {
            //         primary: "ui-icon-wrench"
            //     },
            //     text: false
            // });
            this.$("#delete").button({
                icons: {
                    primary: "ui-icon-trash"
                },
                text: false
            }).click(function () {
                var deleteDialog = new DeleteVirtualDeviceDialog({
                    model: that.model,
                    onDeleted: function () {
                        Backbone.history.navigate("/scp/");
                        that.options.onDeleted();
                        that.destroy();
                    }
                });
            });
        },

        destroy: function () {
            this.$('#refresh').button('destroy');
            this.$('#delete').button('destroy');
            this.$el.empty();
        }

    });
    return DeviceVirtualView;
});
