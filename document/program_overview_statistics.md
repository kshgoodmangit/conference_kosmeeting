# Program at a Glance statistics

The public controller replaces `.program-overview-stats` inside sanitized CMS HTML
on each request using the current conference's program data. Surrounding CMS content
and existing layout classes remain editable. No database migration is required.

- **Conference Days**: enabled program days (the same visibility rule as Scientific Program).
- **Scientific Sessions**: enabled, top-level `SESSION` items on enabled days whose
  titles start with `Session N` (case insensitive). The current data also classifies
  Flash Talks and Poster Session I as `SESSION`, so counting every `SESSION` would
  incorrectly include those formats. Keep the numbered naming convention when adding
  scientific sessions; changing this convention requires updating the classification.
- **Plenary & Presidential Lectures**: enabled, top-level `PLENARY` items on enabled days.
  The current Presidential Lecture uses this type.

Empty schedules display `00`. Program query failures are not converted into zero counts.
The current schedule yields `04 / 07 / 06`. CMS placeholder numbers are ignored on
the public page; retain the statistics container class when editing the overview.
