$(document).ready(function() {
    $(".show").click(function() {
        if (!$(this).next(".details").is(":visible")) {
            $(this).next(".details").slideDown(100);
            $(this).text("Hide details");         
        } else {
            $(this).next(".details").slideUp(100);
            $(this).text("Show details");
        }
    });
    $(".check").click(function(){
        if ($(this).hasClass('checked')) {
            $(this).removeClass('checked');
        } else {
            $(this).addClass('checked');
        }
        applyFilters();
    });
    applyFilters();
});

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