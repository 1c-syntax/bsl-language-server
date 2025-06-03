А = "Short line without comment";

А = "This is a line that exceeds 120 characters limit without any comments at all and should always be flagged in all cases";

А = "Short line"; // with a trailing comment that makes the total length exceed 120 characters limit when included in the calculation

А = "This is a long line that already exceeds 120 characters on its own without any trailing comment appended"; // with even more text

// This is just a comment line that is very long and exceeds 120 characters limit but is not a trailing comment at all