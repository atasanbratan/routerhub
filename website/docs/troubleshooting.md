# Troubleshooting

## The router can't be reached

- Your phone must be on the router's network (its Wi-Fi, not mobile data).
- Check the address: open it in the phone's browser. If the router's web page doesn't load there, RouterHub
  can't reach it either.
- Some routers only accept one signed-in session per device or address. Sign out of the router's web page
  on other devices and try again.

## "Wrong password", or the router locked its login

Use the router's admin password, not the Wi-Fi password. Several routers lock their login for a while after a
few wrong attempts, counting attempts from a browser too. Wait a few minutes and try again.

## My extension doesn't show up

- Open **Browse → Extensions**. An extension from an unknown signer is listed with a **Trust** button and is
  not loaded until you tap it.
- If it says "Built for router-api vN; this app supports …", the extension needs a newer app:
  [update the app](/download).

## Something else

Copy the debug information from **More → About → Version** (tap it) and
[open an issue](https://github.com/atasanbratan/routerhub/issues/new/choose) with it.
