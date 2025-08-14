package coursier.core.compatibility

object Entities {

  // Generated via https://gist.github.com/alexarchambault/79388ff31ec8cbddf6607b55ab2f6527
  val entities = Vector(
    ("&nbsp;", "&#160;"),
    ("&iexcl;", "&#161;"),
    ("&cent;", "&#162;"),
    ("&pound;", "&#163;"),
    ("&curren;", "&#164;"),
    ("&yen;", "&#165;"),
    ("&brvbar;", "&#166;"),
    ("&sect;", "&#167;"),
    ("&uml;", "&#168;"),
    ("&copy;", "&#169;"),
    ("&ordf;", "&#170;"),
    ("&laquo;", "&#171;"),
    ("&not;", "&#172;"),
    ("&shy;", "&#173;"),
    ("&reg;", "&#174;"),
    ("&macr;", "&#175;"),
    ("&deg;", "&#176;"),
    ("&plusmn;", "&#177;"),
    ("&acute;", "&#180;"),
    ("&micro;", "&#181;"),
    ("&para;", "&#182;"),
    ("&middot;", "&#183;"),
    ("&cedil;", "&#184;"),
    ("&ordm;", "&#186;"),
    ("&raquo;", "&#187;"),
    ("&iquest;", "&#191;"),
    ("&Agrave;", "&#192;"),
    ("&Aacute;", "&#193;"),
    ("&Acirc;", "&#194;"),
    ("&Atilde;", "&#195;"),
    ("&Auml;", "&#196;"),
    ("&Aring;", "&#197;"),
    ("&AElig;", "&#198;"),
    ("&Ccedil;", "&#199;"),
    ("&Egrave;", "&#200;"),
    ("&Eacute;", "&#201;"),
    ("&Ecirc;", "&#202;"),
    ("&Euml;", "&#203;"),
    ("&Igrave;", "&#204;"),
    ("&Iacute;", "&#205;"),
    ("&Icirc;", "&#206;"),
    ("&Iuml;", "&#207;"),
    ("&ETH;", "&#208;"),
    ("&Ntilde;", "&#209;"),
    ("&Ograve;", "&#210;"),
    ("&Oacute;", "&#211;"),
    ("&Ocirc;", "&#212;"),
    ("&Otilde;", "&#213;"),
    ("&Ouml;", "&#214;"),
    ("&times;", "&#215;"),
    ("&Oslash;", "&#216;"),
    ("&Ugrave;", "&#217;"),
    ("&Uacute;", "&#218;"),
    ("&Ucirc;", "&#219;"),
    ("&Uuml;", "&#220;"),
    ("&Yacute;", "&#221;"),
    ("&THORN;", "&#222;"),
    ("&szlig;", "&#223;"),
    ("&agrave;", "&#224;"),
    ("&aacute;", "&#225;"),
    ("&acirc;", "&#226;"),
    ("&atilde;", "&#227;"),
    ("&auml;", "&#228;"),
    ("&aring;", "&#229;"),
    ("&aelig;", "&#230;"),
    ("&ccedil;", "&#231;"),
    ("&egrave;", "&#232;"),
    ("&eacute;", "&#233;"),
    ("&ecirc;", "&#234;"),
    ("&euml;", "&#235;"),
    ("&igrave;", "&#236;"),
    ("&iacute;", "&#237;"),
    ("&icirc;", "&#238;"),
    ("&iuml;", "&#239;"),
    ("&eth;", "&#240;"),
    ("&ntilde;", "&#241;"),
    ("&ograve;", "&#242;"),
    ("&oacute;", "&#243;"),
    ("&ocirc;", "&#244;"),
    ("&otilde;", "&#245;"),
    ("&ouml;", "&#246;"),
    ("&divide;", "&#247;"),
    ("&oslash;", "&#248;"),
    ("&ugrave;", "&#249;"),
    ("&uacute;", "&#250;"),
    ("&ucirc;", "&#251;"),
    ("&uuml;", "&#252;"),
    ("&yacute;", "&#253;"),
    ("&thorn;", "&#254;"),
    ("&yuml;", "&#255;")
  )

  lazy val map = entities.toMap

  def map(s: String) = s match {
    case "&nbsp;"   => "&#160;"
    case "&iexcl;"  => "&#161;"
    case "&cent;"   => "&#162;"
    case "&pound;"  => "&#163;"
    case "&curren;" => "&#164;"
    case "&yen;"    => "&#165;"
    case "&brvbar;" => "&#166;"
    case "&sect;"   => "&#167;"
    case "&uml;"    => "&#168;"
    case "&copy;"   => "&#169;"
    case "&ordf;"   => "&#170;"
    case "&laquo;"  => "&#171;"
    case "&not;"    => "&#172;"
    case "&shy;"    => "&#173;"
    case "&reg;"    => "&#174;"
    case "&macr;"   => "&#175;"
    case "&deg;"    => "&#176;"
    case "&plusmn;" => "&#177;"
    case "&acute;"  => "&#180;"
    case "&micro;"  => "&#181;"
    case "&para;"   => "&#182;"
    case "&middot;" => "&#183;"
    case "&cedil;"  => "&#184;"
    case "&ordm;"   => "&#186;"
    case "&raquo;"  => "&#187;"
    case "&iquest;" => "&#191;"
    case "&Agrave;" => "&#192;"
    case "&Aacute;" => "&#193;"
    case "&Acirc;"  => "&#194;"
    case "&Atilde;" => "&#195;"
    case "&Auml;"   => "&#196;"
    case "&Aring;"  => "&#197;"
    case "&AElig;"  => "&#198;"
    case "&Ccedil;" => "&#199;"
    case "&Egrave;" => "&#200;"
    case "&Eacute;" => "&#201;"
    case "&Ecirc;"  => "&#202;"
    case "&Euml;"   => "&#203;"
    case "&Igrave;" => "&#204;"
    case "&Iacute;" => "&#205;"
    case "&Icirc;"  => "&#206;"
    case "&Iuml;"   => "&#207;"
    case "&ETH;"    => "&#208;"
    case "&Ntilde;" => "&#209;"
    case "&Ograve;" => "&#210;"
    case "&Oacute;" => "&#211;"
    case "&Ocirc;"  => "&#212;"
    case "&Otilde;" => "&#213;"
    case "&Ouml;"   => "&#214;"
    case "&times;"  => "&#215;"
    case "&Oslash;" => "&#216;"
    case "&Ugrave;" => "&#217;"
    case "&Uacute;" => "&#218;"
    case "&Ucirc;"  => "&#219;"
    case "&Uuml;"   => "&#220;"
    case "&Yacute;" => "&#221;"
    case "&THORN;"  => "&#222;"
    case "&szlig;"  => "&#223;"
    case "&agrave;" => "&#224;"
    case "&aacute;" => "&#225;"
    case "&acirc;"  => "&#226;"
    case "&atilde;" => "&#227;"
    case "&auml;"   => "&#228;"
    case "&aring;"  => "&#229;"
    case "&aelig;"  => "&#230;"
    case "&ccedil;" => "&#231;"
    case "&egrave;" => "&#232;"
    case "&eacute;" => "&#233;"
    case "&ecirc;"  => "&#234;"
    case "&euml;"   => "&#235;"
    case "&igrave;" => "&#236;"
    case "&iacute;" => "&#237;"
    case "&icirc;"  => "&#238;"
    case "&iuml;"   => "&#239;"
    case "&eth;"    => "&#240;"
    case "&ntilde;" => "&#241;"
    case "&ograve;" => "&#242;"
    case "&oacute;" => "&#243;"
    case "&ocirc;"  => "&#244;"
    case "&otilde;" => "&#245;"
    case "&ouml;"   => "&#246;"
    case "&divide;" => "&#247;"
    case "&oslash;" => "&#248;"
    case "&ugrave;" => "&#249;"
    case "&uacute;" => "&#250;"
    case "&ucirc;"  => "&#251;"
    case "&uuml;"   => "&#252;"
    case "&yacute;" => "&#253;"
    case "&thorn;"  => "&#254;"
    case "&yuml;"   => "&#255;"
    case s          => s
  }

}
