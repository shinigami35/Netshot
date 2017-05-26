/**
 * Created by agm on 16/05/2017.
 */
define([
        'jquery',
        'underscore',
        'backbone',
        'text!templates/scp/scp.html',
        'text!templates/scp/scpListItem.html',
        'text!templates/scp/scpListApp.html',
        'text!templates/scp/toolbar/scpToolbar.html',
        'text!templates/scp/companyListItem.html',
        'text!templates/scp/tableau/scpLineTableau.html',
        'views/scp/ScpView',
        'views/scp/dialog/AddScpDeviceDialog',
        'views/scp/dialog/AddScpCompanyDialog',
        'models/scp/device/DeviceVirtualCollection',
        'models/scp/company/CompanyCollection',
        'models/scp/type/TypeCollection'
    ],
    function ($, _, Backbone, Scptemplate, ScpList, ScpApp, ScpToolbar,
              CompanyListItem, ScpLineTableau, ScpViews, ScpDialogAdd,
              ScpDialogCompany, DeviceVirtualCollection, CompanyCollection, TypeCollection) {
        makeLoadProgress(15);

        var view = Backbone.View.extend({

            el: $("#page"),

            template: _.template(Scptemplate),
            itemTemplate: _.template(ScpList),
            appTemplate: _.template(ScpApp),
            toolbarTemplate: _.template(ScpToolbar),
            companyListItem: _.template(CompanyListItem),
            lineTableauTemplate: _.template(ScpLineTableau),

            events: {
                "input #virtual": "changeBrowser",
                "change #companyselect": "changeCompany"
            },

            initialize: function (option) {
                this.devices = new DeviceVirtualCollection([]);
                this.company = new CompanyCollection([]);
                this.device = null;
                this.deviceName = [];

            },

            changeBrowser: function (e) {
                var that = this;
                e.preventDefault();
                var value = $(e.currentTarget).val();
                this.deviceName.forEach(function (e) {
                    if (e.name === value) {
                        var device = that.devices.get(e.id);
                        device = device.toJSON();
                        that.line = "";
                        device.file.forEach(function (elt) {
                            that.line += that.lineTemplate(elt);
                        });
                        if (that.line !== "") {
                            that.$("#bodytableau").html("");
                            that.$("#bodytableau").html(that.line);
                            that.$("#virtualpage")[0].style.display = 'block';
                        } else {
                            that.$("#bodytableau").html("");
                            that.$("#virtualpage")[0].style.display = 'none';
                        }
                    }
                })
            },

            changeCompany: function (e) {
                var that = this;
                e.preventDefault();
                var value = $(e.currentTarget).val();
                if (value === 'ALL') {
                    that.renderDeviceList();
                } else {
                    var company = that.company.get(value).toJSON();
                    var newHtml = "";
                    that.devices.models.forEach(function (elt) {
                        if (company.name === elt.toJSON().company.name)
                            newHtml += that.itemTemplate(elt.toJSON())
                    });
                    that.$("#nsdevices-listbox-virtual>ul").html("");
                    that.$("#nsdevices-listbox-virtual>ul").html(newHtml);
                }
            },

            render: function () {
                var that = this;
                this.$el.html(this.template);
                this.initFetchDevices();
                $('#nstoolbar-section').html(this.toolbarTemplate);
                $('#nstoolbar-section button').button();
                $('#nstoolbar-devices-add').unbind('click').button()
                    .click(function () {
                        var scp_dialog_add = new ScpDialogAdd();
                    });
                $('#nstoolbar-company-add').unbind('click').button()
                    .click(function () {
                        var scp_dialog_company = new ScpDialogCompany();
                    });
            },

            renderDeviceList: function () {
                var that = this;
                this.htmlBufferCompany = "";
                this.htmlBuffer = "";

                this.devices.each(this.renderDeviceListItem, this);
                this.devices.each(this.getNameVirtual, this);
                this.company.each(this.renderCompanyListApp, this);

                this.$("#nsdevices-listbox-virtual>ul").html(this.htmlBuffer);
                this.$("#nsdevices-groups>ul").html(this.htmlBufferCompany);
                this.$("#nsdevices-groups>ul li.nsdevices-list-group").unbind()
                    .click(function () {
                        if ($(this).hasClass("active")) return;
                        var id = $(this).data('group-id');
                        var company = that.company.get(id).toJSON();
                        var newHtml = "";
                        that.devices.models.forEach(function (elt) {
                            if (company.name === elt.toJSON().company.name)
                                newHtml += that.itemTemplate(elt.toJSON())
                        });
                        that.$("#companyselect").val(id);
                        that.$("#nsdevices-listbox-virtual>ul").html("");
                        that.$("#nsdevices-listbox-virtual>ul").html(newHtml);
                    });

                this.renderButton();

                this.$("#nsdevices-listbox-virtual>ul li.nsdevices-list-device").unbind()
                    .click(function () {
                        if ($(this).hasClass("active")) return;
                        var id = $(this).data('id');
                        var device = that.devices.get(id);
                        device = device.toJSON();
                        var line = "";
                        device.file.forEach(function (elt) {
                            line += that.lineTableauTemplate(elt);
                        });
                        if (line !== "") {
                            that.$("#nameMac").val(device.name);
                            that.$("#general>tbody").html(line);
                        } else {
                            that.$("#nameMac").val(device.name);
                            that.$("#general>tbody").html("");
                        }
                    });

                this.$("#companyselect option").each(function () {
                    $(this).remove();
                });
                $('<option />').attr('value', 'ALL')
                    .text('ALL').appendTo(that.$('#companyselect'));
                _.each(this.company.models, function (c) {
                    $('<option />').attr('value', c.get('id'))
                        .text(c.get('name')).appendTo(that.$('#companyselect'));
                });
            },

            getNameVirtual: function (device) {
                var d = device.toJSON();
                var tmp = {
                    id: d.id,
                    name: d.name
                };
                this.deviceName.push(tmp);
            },

            renderDeviceListItem: function (device) {
                this.htmlBuffer += this.itemTemplate(device.toJSON());
            },

            renderCompanyListApp: function (company) {
                this.htmlBufferCompany += this.companyListItem(company.toJSON());
            },

            initFetchDevices: function () {
                var that = this;
                $.when(this.devices.fetch(), this.company.fetch()).done(function () {
                    that.renderDeviceList();
                });
            },

            renderButton: function () {
                this.$('#refresh').button({
                    icons: {
                        primary: "ui-icon-refresh"
                    },
                    text: false
                });
                this.$('#edit').button({
                    icons: {
                        primary: "ui-icon-wrench"
                    },
                    text: false
                });
                this.$("#delete").button({
                    icons: {
                        primary: "ui-icon-trash"
                    },
                    text: false
                });
            }
        });
        return view;
    });