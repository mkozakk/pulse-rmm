#ifndef H264_WINDOWS_H
#define H264_WINDOWS_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct H264Encoder H264Encoder;

// Initialize H264 encoder with desktop capture
// Returns encoder context or NULL on error
H264Encoder* h264_init(void);

// Get desktop width and height
void h264_get_dimensions(H264Encoder* enc, int* width, int* height);

// Capture and encode next frame
// Returns NAL unit data (caller must free)
// Returns NULL and sets *out_len=0 if no frame available
uint8_t* h264_read_frame(H264Encoder* enc, int* out_len);

// Free frame data returned by h264_read_frame
void h264_free_frame(uint8_t* data);

// Close encoder and release resources
void h264_close(H264Encoder* enc);

// Get last error message
const char* h264_get_error(H264Encoder* enc);

#ifdef __cplusplus
}
#endif

#endif // H264_WINDOWS_H
