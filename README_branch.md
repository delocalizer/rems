`feat-base-url` is to support the request in https://github.com/CSCfi/rems/issues/3363.

## What is wanted

I would like a configuration point base-url that defaults to / but can be changed to something else e.g. /app1/, so that the REMS application paths are all relative to that base URL.

## Suggested approach from @macroz

> These days REMS 2 is a modern single-page app (SPA) and most URLs are created in the front-end, i.e. the web app.
> 
> The problem is multifaceted. First of all the backend, which does less URL manipulation, must be updated to respond to basically all requests within the new "app path context" and not any others (to not require a separate router in front of REMS). Then the larger part of the problem is in the front-end URL routes and generating URLs.
> 
> Basically at least places where :public-url is used, should potentially handle something like :app-path. It should also define whether the app path is in addition to the public URL.
> The backend for the front-end routes must all support this. Many do redirects after logging in. (see Compojure routes e.g. defroutes in handler.clj)
> The API routes must all support that the API is not at the root /api (see compojure-api routes in api.clj)
> The single-page app routes must all support this. Possibly Accountant "interceptor" could handle removing the app path from the path and then just re-use the existing route definitions. (see Secretary and Accountant in app.cljs
> There are many places within the front-end \*.cljs files where a URL is produced or manipulated, and these should either be changed to use relative URLs or generate (using config) a proper URL including app path.
> The tests should test this new feature, likely using browser tests.
