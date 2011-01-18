<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=utf-8">
<link rel="STYLESHEET" type="text/css" href="html/jugglinglab.css">
<title>Juggling Lab siteswap animation</title>
</head>
<body>
<div id="standalone">
<?php
$query=urldecode($_SERVER["QUERY_STRING"]);
if ($query=="") {
	echo "<p>This page is a PHP front end to <a href=\"http://jugglinglab.sourceforge.net\">Juggling Lab</a>.</p>";
	echo "<p>For information on animating patterns with Juggling Lab in this way, please see <a href=\"html/phpinfo.html\">the instructions</a>.</p>";
} else {
	parse_str(strtr($query, ";", "&"));
	$width = (($stereo=="true") ? 750 : 600);
	$viewmode = ( (strpos($query, "<") === false) ? "edit" : "simple" );
	echo "<p>\n";
	echo "<applet archive=\"bin/JugglingLab.jar\" code=\"JugglingLab\" width=\"$width\" height=\"500\">\n";
	echo "<param name=\"config\" value=\"entry=none;view=$viewmode\">\n";
	echo "<param name=\"animprefs\" value=\"$query\">\n";
	echo "<param name=\"notation\" value=\"siteswap\">\n";
	echo "<param name=\"pattern\" value=\"$query\">\n";
	echo "Java not available\n";
	echo "</applet>\n";
	echo "</p>\n";
	echo "<p>\n";
	echo "<b>Animator input (<a href=\"html/phpinfo.html\">instructions</a>):</b><br>\n";
	echo $query;
	echo "</p>\n";
	if ($stereo == "true") {
		echo "<p><i>Note: To view this stereogram, cross your eyes slightly so that
the images appear to merge.</i></p>";
	}
}
?>
</div>
</body>
</html>
