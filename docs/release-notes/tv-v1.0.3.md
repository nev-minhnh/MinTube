MinTube TV 1.0.3 fixes the Upload date filter and changes how publish time reads.

## Fixed

- **The Upload date filter no longer fails when you are signed in.** MinTube now sends the filter itself when YouTube leaves out its filter options, and checks each video's publish time against the limit, including rounded ages such as "3 weeks ago".

## Changed

- **Publish time uses whole units.** With a real date, an age under 12 months uses the largest unit that fits exactly (24 hours is 1 day, 25 hours stays 25 hours; 7 days is 1 week, 8 days stays 8 days; 30 days is 1 month). Older than 12 months, the date shows as dd/mm/yyyy. YouTube's own rounded text follows the same rule ("7 days ago" reads "1 week ago"); "2 years ago" is kept, since the exact date is unknown.

MinTube TV is part of MinTube, a fork of Litube 4.0.0 by HydeYYHH, licensed under GPL-3.0.
