import os
import sys
import io

# Ensure UTF-8 encoding for stdout on Windows to handle Unicode characters
if sys.platform == 'win32':
    sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')


def verify_archive_size(archive_path, min_size_mb=1):
    """
    Verify that the archive file exists and is larger than the minimum size.
    
    Args:
        archive_path: Path to the archive file
        min_size_mb: Minimum size in megabytes (default: 1)
    
    Returns:
        0 if validation passes, 1 otherwise
    """
    min_size_bytes = min_size_mb * 1024 * 1024  # Convert MB to bytes
    
    # Check if file exists
    if not os.path.isfile(archive_path):
        print(f"Error: Archive file '{archive_path}' not found")
        return 1
    
    # Get file size
    file_size = os.path.getsize(archive_path)
    
    # Print information
    print(f"Archive file: {archive_path}")
    print(f"Archive size: {file_size:,} bytes ({file_size / (1024 * 1024):.2f} MB)")
    print(f"Minimum required size: {min_size_bytes:,} bytes ({min_size_mb} MB)")
    
    # Validate size
    if file_size < min_size_bytes:
        print(f"Error: Archive size is less than {min_size_mb} MB")
        return 1
    
    print("✓ Archive size validation passed")
    return 0


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python verify-archive-size.py <archive_file> [min_size_mb]")
        print("  archive_file: Path to the archive file to verify")
        print("  min_size_mb: Minimum size in megabytes (integer, default: 1)")
        sys.exit(1)
    
    archive_file = sys.argv[1]
    
    # Parse min_size with error handling
    min_size = 1  # default
    if len(sys.argv) > 2:
        try:
            min_size = int(sys.argv[2])
            if min_size <= 0:
                print("Error: min_size_mb must be a positive integer")
                sys.exit(1)
        except ValueError:
            print(f"Error: Invalid min_size_mb '{sys.argv[2]}'. Must be an integer.")
            sys.exit(1)
    
    exit_code = verify_archive_size(archive_file, min_size)
    sys.exit(exit_code)
