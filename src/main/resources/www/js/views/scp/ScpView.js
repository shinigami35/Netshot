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
        'views/scp/ScpView',
        'views/scp/dialog/AddScpDeviceDialog',
        'views/scp/dialog/AddScpCompanyDialog',
        'models/scp/device/DeviceVirtualCollection',
        'models/scp/company/CompanyCollection',
        'views/scp/VirtualDeviceView'
    ],
    function ($, _, Backbone, Scptemplate, ScpList, ScpApp, ScpToolbar,
              ScpViews, ScpDialogAdd,
              ScpDialogCompany, DeviceVirtualCollection, CompanyCollection, VirtualDeviceView) {
        makeLoadProgress(15);

        var view = Backbone.View.extend({

            el: $("#page"),

            template: _.template(Scptemplate),
            itemTemplate: _.template(ScpList),
            appTemplate: _.template(ScpApp),
            toolbarTemplate: _.template(ScpToolbar),


            events: {
                "input #virtual": "changeBrowser",
                "change #companyselect": "changeCompany"
            },

            initialize: function (option) {
                this.devices = new DeviceVirtualCollection([]);
                this.company = new CompanyCollection([]);
                this.deviceVirtualView = null;
                this.device = null;
                this.deviceName = [];
                this.id = option.id;

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
                        var scp_dialog_add = new ScpDialogAdd({
                            company : that.company,
                            onRefresh: function () {
                                that.initFetchDevices();
                            }
                        });
                    });
                $('#nstoolbar-company-add').unbind('click').button()
                    .click(function () {
                        var scp_dialog_company = new ScpDialogCompany({
                                onRefresh: function () {
                                    that.initFetchDevices();
                                }
                            }
                        );
                    });
            },

            renderDeviceList: function () {
                var that = this;
                this.htmlBuffer = "";

                this.devices.each(this.renderDeviceListItem, this);
                this.devices.each(this.getNameVirtual, this);

                this.$("#nsdevices-listbox-virtual>ul").html(this.htmlBuffer);


                this.$("#companyselect option").each(function () {
                    $(this).remove();
                });
                $('<option />').attr('value', 'ALL')
                    .text('ALL').appendTo(that.$('#companyselect'));
                _.each(this.company.models, function (c) {
                    $('<option />').attr('value', c.get('id'))
                        .text(c.get('name')).appendTo(that.$('#companyselect'));
                });
                this.decorateDeviceList();

                if (this.id) {
                    that.renderDevice(this.id);
                    that.highlightDevice(this.id);
                }
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


            initFetchDevices: function () {
                var that = this;
                $.when(this.devices.fetch(), this.company.fetch()).done(function () {
                    that.renderDeviceList();
                });
            },

            decorateDeviceList: function () {
                var that = this;
                this.$("#nsdevices-listbox-virtual>ul li").unbind().mouseenter(function () {
                    var $this = $(this);
                    if (!$this.hasClass("active")) {
                        $this.addClass("hover");
                    }
                }).mouseleave(function () {
                    $(this).removeClass("hover");
                }).click(function (e) {
                    if ($(this).hasClass("active")) return;
                    var id = $(this).data('id');
                    that.renderDevice(id);
                    that.highlightDevice(id);
                    return false;
                });
            },
            highlightDevice: function (id) {
                var item = this.getDeviceListItem(id);
                this.$('#nsdevices-listbox-virtual>ul li.active').removeClass('active');
                if (item.length > 0) {
                    item.removeClass("hover").addClass("active");
                    if (item.position().top > this.$('#nsdevices-listbox-virtual').height() - 30) {
                        this.$('#nsdevices-listbox-virtual').scrollTop(item.position().top
                            + this.$('#nsdevices-listbox-virtual').scrollTop());
                    }
                }
                else {
                    this.device = null;
                    this.renderDevice();
                }
            },
            getDeviceListItem: function (id) {
                if (typeof id === "undefined") {
                    id = this.device.get('id');
                }
                return this.$('#nsdevices-listbox-virtual>ul li[data-id="' + id + '"]');
            },
            //TODO
            renderDevice: function (id) {
                var that = this;
                if (id !== "undefined")
                    this.device = this.devices.get(id);
                if (this.deviceVirtualView != null) this.deviceVirtualView.destroy();
                if (this.device != null) {
                    this.deviceVirtualView = new VirtualDeviceView({
                        model: this.device,
                        onEdited: function () {
                            that.device = that.deviceView.model;
                            that.rerenderDeviceListItem();
                            //that.decorateDeviceList();
                        },
                        onDeleted: function () {
                            that.device = that.deviceVirtualView.model;
                            that.deleteVirtualDeviceListItem();
                            that.devices.remove(that.device);
                            that.device = null;
                            that.renderDeviceList();
                        }
                    });
                    this.deviceVirtualView.render();
                }
            },
            deleteVirtualDeviceListItem: function () {
                this.getDeviceListItem().remove();
            },
        });


        return view;
    });