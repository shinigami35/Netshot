/** Copyright 2013-2014 NetFishers */
define([
    'jquery',
    'underscore',
    'backbone',
    'views/Dialog',
    'models/scp/device/DeviceVirtualModel',
    'models/scp/company/CompanyCollection',
    'models/scp/type/TypeCollection',
    'text!templates/scp/dialog/addScpDevice.html'
], function ($, _, Backbone, Dialog, DeviceVirtualModel, CompanyCollection, TypeCollection, addScpDeviceTemplate) {

    return Dialog.extend({

        template: _.template(addScpDeviceTemplate),

        initialize: function (options) {
            var that = this;
            this.model = new DeviceVirtualModel();
            this.companySelect = null;
            this.typeSelect = null;
            this.company = options.company;
            this.type = new TypeCollection([]);
            $.when(this.type.fetch()).done(function () {
                that.render();
            });
        },

        dialogOptions: {
            title: "Add device Virtual"
        },
        events: {
            "change #devicecompany": "setPath",
            "change #devicetype": "setType"
        },

        setType: function (e) {
            e.preventDefault();
            var that = this;
            var type_id = that.$('#devicetype').val();
            var name = that.$('#devicename').val();
            if (type_id === "nil")
                that.typeSelect = null;
            else {
                var type = that.type.get(parseInt(type_id)).toJSON();
                if (type) {
                    that.typeSelect = type.name;
                    if (that.typeSelect && that.companySelect
                        && that.typeSelect !== "nil" && that.typeSelect !== "nil") {
                        that.$('#devicefolder')[0].value = "";
                        that.$('#devicefolder').val("/" + that.companySelect + "/" + that.typeSelect + "/" + name + "/");
                    }
                }
            }

        },
        setPath: function (e) {
            e.preventDefault();
            var that = this;
            var company_id = that.$('#devicecompany').val();
            var name = that.$('#devicename').val();
            if (company_id === "nil") {
                that.companySelect = null;
                that.$('#devicefolder')[0].value = "";
            } else {
                var company = that.company.get(parseInt(company_id)).toJSON();
                if (company) {
                    that.companySelect = company.name;
                    that.$('#devicefolder')[0].value = "";
                    that.$('#devicefolder').val("/" + that.companySelect + "/" + that.typeSelect + "/" + name + "/");
                }
            }
        },

        buttons: {
            "Add": function (event) {
                var that = this;
                var $button = $(event.target).closest("button");
                $button.button('disable');
                var tmpCompany = that.$('#devicecompany').val();
                var tmpType = that.$('#devicetype').val();
                var tmpName = that.$('#devicename').val();
                if (tmpName === "") {
                    that.$("#errormsg").text("Error: You must add a name");
                    that.$("#error").show();
                    $button.button('enable');
                } else if (tmpCompany === "nil") {
                    that.$("#errormsg").text("Error: You must select a company");
                    that.$("#error").show();
                    $button.button('enable');
                } else if (tmpType === "nil") {
                    that.$("#errormsg").text("Error: You must select a type");
                    that.$("#error").show();
                    $button.button('enable');
                } else {
                    that.model.save({
                        type: parseInt(that.$('#devicetype').val(), 10),
                        name: that.$('#devicename').val(),
                        company: parseInt(that.$('#devicecompany').val(), 10),
                        folder: that.$('#devicefolder').val(),
                        task: that.$('#devicetask').val(),
                        hour: that.$('#devicehour').val(),
                        date: that.formatDate(that.$('#devicetime').datepicker('getDate'))
                    }).done(function (data) {
                        that.options.onRefresh();
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

            _.each(this.company.models, function (c) {
                $('<option />').attr('value', c.get('id'))
                    .text(c.get('name')).appendTo(that.$('#devicecompany'));
            });
            _.each(this.type.models, function (c) {
                $('<option />').attr('value', c.get('id'))
                    .text(c.get('name')).appendTo(that.$('#devicetype'));
            });
            var in10min = new Date();
            in10min.setTime(in10min.getTime() + 10 * 60 * 1000);
            this.$('.nsdatepicker').datepicker({
                dateFormat: "dd/mm/y",
                autoSize: true,
                minDate: 0,
                onSelect: function () {
                }
            }).datepicker('setDate', in10min);
        },
        formatDate: function (date) {
            var d = date;
            var month = '' + (d.getMonth() + 1);
            var day = '' + d.getDate();
            var year = d.getFullYear();

            if (month.length < 2) month = '0' + month;
            if (day.length < 2) day = '0' + day;

            return [day, month, year].join('/');
        }

    });
});
