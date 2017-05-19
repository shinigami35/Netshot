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
        'text!templates/scp/scpLine.html',
        'text!templates/scp/toolbar/scpToolbar.html',
        'text!templates/scp/companyListItem.html',
        'views/scp/ScpView',
        'views/scp/dialog/AddScpDeviceDialog',
        'views/scp/dialog/AddScpCompanyDialog',
        'models/scp/device/DeviceVirtualCollection',
        'models/scp/company/CompanyCollection'
    ],
    function ($, _, Backbone, Scptemplate, ScpList, ScpApp, ScpLine,
              ScpToolbar, CompanyListItem, ScpViews, ScpDialogAdd, ScpDialogCompany, DeviceVirtualCollection, CompanyCollection) {
        makeLoadProgress(15);

        var view = Backbone.View.extend({

            el: $("#page"),

            template: _.template(Scptemplate),
            itemTemplate: _.template(ScpList),
            appTemplate: _.template(ScpApp),
            lineTemplate: _.template(ScpLine),
            toolbarTemplate: _.template(ScpToolbar),
            companyListItem: _.template(CompanyListItem),

            events: {
                "input #virtual": "changeBrowser"
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
            render: function () {
                this.$el.html(this.template);
                this.$("#virtualpage")[0].style.display = 'none';
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
                this.htmlBuffer = "";
                this.htmlBufferApp = "";
                this.htmlBufferCompany = "";
                this.devices.each(this.renderDeviceListItem, this);
                this.devices.each(this.renderDeviceListApp, this);
                this.devices.each(this.getNameVirtual, this);
                this.company.each(this.renderCompanyListApp, this);
                this.$("#nsdevices-listbox-virtual>ul").html(this.htmlBuffer);
                this.$("#nsdevices-groups>ul").html(this.htmlBufferCompany);
                this.$("#virtual").html(this.htmlBufferApp);

                this.$("#nsdevices-groups>ul li.nsdevices-list-group").unbind()
                   .click(function () {
                    if ($(this).hasClass("active")) return;
                    var id = $(this).data('group-id');
                    // TODO => Récupérer les virtuals de cette Company
                });
                var that = this;

                this.$("#nsdevices-listbox-virtual>ul li.nsdevices-list-device").unbind()
                    .click(function () {
                        if ($(this).hasClass("active")) return;
                        var id = $(this).data('id');
                        var device = that.devices.get(id);
                        device = device.toJSON();
                        var line = "";
                        device.file.forEach(function (elt) {
                            line += that.lineTemplate(elt);
                        });
                        if (line !== "") {
                            that.$("#bodytableau").html("");
                            that.$("#bodytableau").html(line);
                            that.$("#virtualpage")[0].style.display = 'block';
                        } else {
                            that.$("#bodytableau").html("");
                            that.$("#virtualpage")[0].style.display = 'none';
                        }
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
            renderDeviceListApp: function (device) {
                this.htmlBufferApp += this.appTemplate(device.toJSON());
            },
            renderCompanyListApp: function (company) {
                this.htmlBufferCompany += this.companyListItem(company.toJSON());
            },
            initFetchDevices: function () {
                var that = this;
                $.when(this.devices.fetch(), this.company.fetch()).done(function () {
                    that.renderDeviceList();
                });
            }
        });
        return view;
    });