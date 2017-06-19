/** Copyright 2013-2014 NetFishers */
define([
    'jquery',
    'underscore',
    'backbone',
    'views/Dialog',
    'models/user/UserSshModel',
    'text!templates/admin/addSshUser.html'
], function ($, _, Backbone, Dialog, UserSshModel, addUserSshTemplate) {

    return Dialog.extend({

        template: _.template(addUserSshTemplate),


        events: {
            'change input[type=radio]': "changeAuth"
        },

        changeAuth: function (e) {
            e.preventDefault();
            var that = this;

            var value = document.querySelector('input[name = "auth"]:checked').value;
            if (value === "password") {
                this.$("#password").parent().show();
                this.$("#certificat").parent().hide();
                that.$('#password').val("");
                that.$('#certificat').val("");
            } else if (value === "certificat") {
                this.$("#password").parent().hide();
                this.$("#certificat").parent().show();
                that.$('#certificat').val("");
                that.$('#password').val("");
            }
        },

        initialize: function () {
            this.model = new UserSshModel();
            this.render();
        },

        dialogOptions: {
            title: "Add user ssh"
        },

        buttons: {
            "Add": function (event) {
                var that = this;
                var $button = $(event.target).closest("button");
                $button.button('disable');
                this.model.save({
                    'name': that.$('#name').val(),
                    'password': that.$('#password').val(),
                    'certificat': that.$('#certificat').val()
                }).done(function (data) {
                    that.close();
                    that.options.onAdded();
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
            var that = this;
            this.$("#password").parent().show();
            this.$("#certificat").parent().hide();
        }

    });
});
