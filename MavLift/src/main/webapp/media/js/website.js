$(document).ready(function() {

	// When a user wants to add information to management table.
	$('table.management td a.add').click(function() {
		$(this).replaceWith('<input class="edit" type="text" autofocus="true" /><input class="submit" type="image" src="/media/images/submit.png" />');

		return false;
	});

	// When a user wants to edit information to management table.
	$('table.management td a.edit').click(function() {
		$(this).next().remove();
		$(this).replaceWith('<input class="edit" type="text" value="" autofocus="true" /><input class="submit" type="image" src="/media/images/submit.png" />');

		return false;
	});

});