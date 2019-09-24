$(document).ready(function() {
    var levelFilter   = $('#level');
    var contextFilter = $('#context');

    function showDetails() {
        if (!$(this).next(".details").is(":visible")) {
            $(this).next(".details").slideDown(100);
            $(this).text("Hide details");
        } else {
            $(this).next(".details").slideUp(100);
            $(this).text("Show details");
        }
    }

    function showStack() {
        if (!$(this).next(".stack").is(":visible")) {
            $(this).next(".stack").slideDown(100);
            $(this).text("Hide stack");
        } else {
            $(this).next(".stack").slideUp(100);
            $(this).text("Show stack");
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