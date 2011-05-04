*{
 *  Outputs a <link> tag referencing the src file, and appends a theme query
 *  string parameter.
}*
%{ _src = _src ? _src : _arg }%
<link rel="stylesheet" href="${_src}?theme=${_theme}" type="text/css">
