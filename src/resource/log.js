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
    applyFilters();
});

function applyFilters() {
    $(".switch").each(function() {
        var level = $(this).attr("data");
        if ($(this).prop('checked')) {
            $("tr."+level).show();
        } else {
            $("tr."+level).hide();
        }
    });
}

function searchRecords() {
    var find = $("#finder").val();
    console.log("Find: "+find);
    $(".msg").each(function() {
        if ($(this).text().indexOf(find) !== -1) {
            console.log($(this).text());
        }
    })
}