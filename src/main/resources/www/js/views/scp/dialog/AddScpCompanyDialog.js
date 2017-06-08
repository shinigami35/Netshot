/** Copyright 2013-2014 NetFishers */
define([
    'jquery',
    'underscore',
    'backbone',
    'views/Dialog',
    'models/scp/company/CompanyModel',
    'text!templates/scp/dialog/addScpCompany.html'
], function ($, _, Backbone, Dialog, CompanyModel, addScpCompany) {

    return Dialog.extend({

        template: _.template(addScpCompany),

        initialize: function (options) {
            this.model = new CompanyModel();
            this.render();
        },

        dialogOptions: {
            title: "Add Company",
        },

        buttons: {
            "Add": function (event) {
                var that = this;
                var $button = $(event.target).closest("button");
                $button.button('disable');
                that.model.save({
                    name: that.$('#devicename').val()
                }).done(function (data) {
                    that.options.onRefresh();
                    that.close();
                }).fail(function (data) {
                    var error = $.parseJSON(data.responseText);
                    that.$("#errormsg").text("Error: " + error.errorMsg);
                    that.$("#error").show();
                    $button.button('enable');
                });
            },
            "Cancel": function () {
                this.close();
            }
        },

        onCreate: function () {
        }
    });
});
