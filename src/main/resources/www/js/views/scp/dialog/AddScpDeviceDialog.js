/** Copyright 2013-2014 NetFishers */
define([
    'jquery',
    'underscore',
    'backbone',
    'views/Dialog',
    'models/scp/device/DeviceVirtualModel',
    'models/scp/company/CompanyCollection',
    'text!templates/scp/dialog/addScpDevice.html'
], function ($, _, Backbone, Dialog, DeviceVirtualModel, CompanyCollection, addScpDeviceTemplate) {

    return Dialog.extend({

        template: _.template(addScpDeviceTemplate),

        initialize: function (options) {
            var that = this;
            this.model = new DeviceVirtualModel();
            this.company = new CompanyCollection([]);
            this.company.fetch().done(function () {
                that.render();
            });
        },

        dialogOptions: {
            title: "Add device Virtual",
        },
        events: {
            "change #devicecompany": "setPath"
        },

        setPath: function (e) {
            e.preventDefault()
            var that = this;
            var company_id = that.$('#devicecompany').val();
            if (company_id === "nil") {
                that.$('#devicefolder')[0].value = "";
            } else {
                var company = that.company.get(parseInt(company_id)).toJSON();
                if (company) {
                    that.$('#devicefolder')[0].value = "";
                    that.$('#devicefolder').val("/" + company.name + "/");
                }
            }
        },

        buttons: {
            "Add": function (event) {
                var that = this;
                var $button = $(event.target).closest("button");
                $button.button('disable');
                var tmp = that.$('#devicecompany').val();
                if (tmp === "nil") {
                    that.$("#errormsg").text("Error: You must select a company");
                    that.$("#error").show();
                    $button.button('enable');
                } else {
                    that.model.save({
                        name: that.$('#devicename').val(),
                        ip: that.$('#deviceipvirtual').val(),
                        company: parseInt(that.$('#devicecompany').val(), 10),
                        folder: that.$('#devicefolder').val()
                    }).done(function (data) {
                        that.close();
                    }).fail(function (data) {
                        var error = $.parseJSON(data.responseText);
                        that.$("#errormsg").text("Error: " + error.errorMsg);
                        that.$("#error").show();
                        $button.button('enable');
                    });
                }
            },
            "Cancel": function () {
                this.close();
            }
        },

        onCreate: function () {
            var that = this;
            console.log("OnCreate");

            _.each(this.company.models, function (c) {
                $('<option />').attr('value', c.get('id'))
                    .text(c.get('name')).appendTo(that.$('#devicecompany'));
            });
        }

    });
});
