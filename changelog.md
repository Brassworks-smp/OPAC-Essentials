### Added

* Claim owner search directly in Xaero’s World Map
* Automatic centering on the first matching claim cluster
* Previous and next controls for navigating disconnected claim clusters
* Highly visible yellow outline around the currently selected claim cluster
* Adaptive search icon positioning across different Xaero’s World Map versions

### Changed

* Claim searching now uses incremental snapshots and asynchronous cluster calculation to reduce client-side lag
* Searching centers the map instead of hiding unrelated claims
* Party chat now forwards native OPAC `/opm` errors to the player

### Compatibility

* Requires Xaero’s World Map for the claim search interface
* Supports older Xaero versions with different World Map button layouts