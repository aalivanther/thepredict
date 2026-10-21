# ThePredict migration proposal

ThePredict wrote this proposal and changed nothing else: no file was moved or deleted,
and no query to Jev was enabled. Copy the files below into `plugins/MythicMobs/AIConfig/` by hand.

## Proposed files

{{files}}

## Conversion decisions

{{decisions}}

## Needs manual review

{{manual}}

## How to apply it

1. Stop the server and keep a copy of `plugins/MythicMobs/AIConfig/` and of any old
   `plugins/ThePredict/config.yml` and `plugins/ThePredict/AI/` files (ubicación antigua).
2. Read every decision above and correct the points that need manual review.
3. Copy each `.proposed` file into `plugins/MythicMobs/` by removing the `.proposed` extension.
   `AIConfig/config.yml.proposed` becomes `plugins/MythicMobs/AIConfig/config.yml`. Do this by hand.
4. The proposed `config.yml` has an empty `jev.apiKey`. Set the `TYPESAFE_API_KEY` environment variable
   (or `jev.apiKey`) yourself; an OpenRouter key is never copied because it is not a TypeSafe key.
5. Start the server and run `/thepredict status`.
