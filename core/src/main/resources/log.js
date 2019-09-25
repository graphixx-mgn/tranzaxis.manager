$(document).ready(function() {
    var levelFilter   = $('#level');
    var contextFilter = $('#context');

    function showDetails() {
        var button  = $(this);
        var message = button.parent().find(".msg");
        var details = button.parent().find("div.details");
        if (!details.is(":visible")) {
            message.hide();
            details.slideDown(100);
            button.text("Hide details");
        } else {
            details.slideUp(100);
            message.show();
            button.text("Show details");
        }
    }

    function showStack() {
        var button  = $(this);
        var message = button.parent().find(".msg");
        var stack   = button.parent().find("div.stack");
        if (!stack.is(":visible")) {
            message.hide();
            stack.slideDown(100);
            button.text("Hide stack");
        } else {
            stack.slideUp(100);
            message.show();
            button.text("Show stack");
        }
    }

    function applyFilters() {
        $(".log tbody tr").each(function() {
            if (recordAllowed($(this))) {
                $(this).show();
            } else {
                $(this).hide();
            }
        });
    }
    function recordAllowed(record) {
        var filters = $('.filter');
        for (var i = 0; i < filters.length; i++) {
            if (!filters[i].checkCondition(record)) {
                return false;
            }
        }
        return true;
    }

    levelFilter.find(".check").click(function() {
        if ($(this).hasClass('checked')) {
            $(this).removeClass('checked');
        } else {
            $(this).addClass('checked');
        }
        applyFilters();
    });
    levelFilter.each(function() {
        this.checkCondition = function(record) {
            var level = record.attr("level");
            return $(this).find('.check[data="'+level+'"]').hasClass("checked");
        }
    });


    contextFilter.find("input").change(function() {
        applyFilters();
    });
    contextFilter.each(function() {
        this.checkCondition = function(record) {
            var context = record.attr("context");
            return $(this).find('input[data="'+context+'"]').is(":checked");
        }
    });

    $("a.details").click(showDetails);
    $("a.stack").click(showStack);
});