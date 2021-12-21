# Branding of the OBP API landing page

Look and feel of the API landing page can be modified via css and with a number of variables in the props file (production.default.props).

## CSS

### Main CSS

The main css is located here:

OBP-API/src/main/webapp/media/css/website.css

In case you want to keep it outside of the public code you can specify a new URI via props `webui_main_style_sheet`. For instance:

webui_override_style_sheet = https://static.openbankproject.com/test/css/website.css

### Override CSS

The override css is used if you use `OBP-API/src/main/webapp/media/css/website.css` but you want to override some instance specific values.
In that case you can do it via props `webui_override_style_sheet` i.e.

webui_override_style_sheet = https://static.openbankproject.com/test/css/override.css

where `override.css` could be:

```css
.navbar-default .navbar-nav > li #navitem-logo {
    padding-top: 11px;
    padding-bottom: 11px;
    margin-right: 16px;
    height: 88px;
    margin-left: 0;
}

.navbar-default .navbar-nav > li #navitem-logo img {
	width: 67px;
	height: 67px;
}



#main-about {
    height: 552px;
}
```

## Props

There's a number of props variables starting with webui_* - see OBP-API/src/main/resources/props/sample.props.template for a comprehensive list and default values.
