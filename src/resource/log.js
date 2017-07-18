$(document).ready(function() {
    function showDetails() {
        if (!$(this).next(".details").is(":visible")) {
            $(this).next(".details").slideDown(100);
            $(this).text("Hide details");
        } else {
            $(this).next(".details").slideUp(100);
            $(this).text("Show details");
        }
    }
    function dateToString(date) {
        return (date.getDate() < 10 ? "0"+date.getDate(): date.getDate()) + "-" +
               (date.getMonth() + 1 < 10 ? "0"+(date.getMonth() + 1) : date.getMonth() + 1) + "-" +
                date.getFullYear() + " " +
               (date.getHours() < 10 ? "0"+date.getHours() : date.getHours()) + ":" +
               (date.getMinutes() < 10 ? "0"+date.getMinutes() : date.getMinutes()) + ":" +
               (date.getSeconds() < 10 ? "0"+date.getSeconds() : date.getSeconds());
    }
    function selectLog() {
        var curr = $(".nav ol.tree li.file.active");
        if (curr.length) {
            curr.removeClass("active");
            $(".session[timestamp='"+curr.attr("timestamp")+"']").hide();
        }
        var link    = $(this);
        var session = $(".session[timestamp='"+link.attr("timestamp")+"']");
        link.addClass("active");
        session.show();

        var errCnt = session.find("tr.ERROR").length;
        $("#header h4").text("Log session start time: "+new Date(link.attr("timestamp")-0).toLocaleString());
        $("#header p").html(
                "<font color='"+(errCnt > 0 ? 'red' : 'black')+"'><span>Errors: "+errCnt+"</span>&nbsp;&nbsp;</font>"+
                "<span>Warnings: "+session.find("tr.WARN").length+"</span>&nbsp;&nbsp;"+
                "<span>Messages: "+(session.find("tr").length-1)+"</span>"
        );
    }
    function changeFilters() {
        if ($(this).hasClass('checked')) {
            $(this).removeClass('checked');
        } else {
            $(this).addClass('checked');
        }
        applyFilters();
    }
    function applyFilters() {
        $(".check").each(function() {
            var level = $(this).attr("data");
            if ($(this).hasClass('checked')) {
                $("tr."+level).show();
            } else {
                $("tr."+level).hide();
            }
        });
    }
    function buildTree() {
        var today = new Date();
        var week  = new Date();
        week.setDate(today.getDate() - 7);

        var todayDir = $(".nav ol.tree li > input#today").parent().find('ol');
        var weekDir  = $(".nav ol.tree li > input#week").parent().find('ol');
        var archDir  = $(".nav ol.tree li > input#archive").parent().find('ol');

        $($(".session").get().reverse()).each(function() {
            var session = $(this);
            var logDate = new Date(session.attr("timestamp")-0);

            var logLink = "<li class='file' timestamp='"+session.attr("timestamp")+"'><a href='#'\">Log session " + dateToString(logDate) + "</a></li>";
            if (today.toDateString() === logDate.toDateString()) {
                todayDir.append(logLink);
            } else if (logDate.getTime() > week.getTime()) {
                var subDir = logDate.toLocaleDateString();
                if (weekDir.find("li label:contains('"+subDir+"')").length === 0) {
                    weekDir.append("<li><label for='"+subDir+"'>"+subDir+"</label> <input type='checkbox' id='"+subDir+"' /><ol></ol></li>");
                    subDir = weekDir.find("li label:contains('"+subDir+"')").parent().find('ol');
                    subDir.append(logLink);
                }
            } else {
                var subDir = logDate.toLocaleDateString();
                if (archDir.find("li label:contains('"+subDir+"')").length === 0) {
                    archDir.append("<li><label for='"+subDir+"'>"+subDir+"</label> <input type='checkbox' id='"+subDir+"' /><ol></ol></li>");
                    subDir = archDir.find("li label:contains('"+subDir+"')").parent().find('ol');
                    subDir.append(logLink);
                }
            }
        });
        if (weekDir.find("li").length === 0) {
            weekDir.closest("li").hide();
        }
        if (archDir.find("li").length === 0) {
            archDir.closest("li").hide();
        }
        $(".nav ol.tree li.file").click(selectLog);
        $(".nav ol.tree li.file:first").click();
    }

    buildTree();
    applyFilters();
    $(".show").click(showDetails);
    $(".check").click(changeFilters);
});