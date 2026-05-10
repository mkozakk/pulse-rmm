#include "h264_windows.h"
#include <d3d11.h>
#include <dxgi1_2.h>
#include <mfapi.h>
#include <mfidl.h>
#include <mferror.h>
#include <wmcodecdsp.h>
#include <comdef.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <queue>
#include <windows.h>

#pragma comment(lib, "d3d11.lib")
#pragma comment(lib, "dxgi.lib")
#pragma comment(lib, "mfplat.lib")
#pragma comment(lib, "mf.lib")
#pragma comment(lib, "wmcodecdsp.lib")
#pragma comment(lib, "shlwapi.lib")

#define SAFE_RELEASE(x) if (x) { x->Release(); x = NULL; }

struct H264Encoder {
    ID3D11Device* d3d_device;
    ID3D11DeviceContext* d3d_context;
    IDXGIOutputDuplication* duplication;
    IMFTransform* encoder;
    DXGI_OUTDUPL_DESC dupl_desc;
    int width;
    int height;
    int frame_count;
    char error_msg[256];
    std::queue<uint8_t*> output_queue;
};

void set_error(H264Encoder* enc, const char* fmt, ...) {
    if (!enc) return;
    va_list args;
    va_start(args, fmt);
    vsnprintf(enc->error_msg, sizeof(enc->error_msg), fmt, args);
    va_end(args);
}

HRESULT create_d3d11_device(ID3D11Device** device, ID3D11DeviceContext** context) {
    D3D_FEATURE_LEVEL feature_levels[] = { D3D_FEATURE_LEVEL_11_0 };
    D3D_FEATURE_LEVEL feature_level;

    return D3D11CreateDevice(
        NULL,                          // pAdapter
        D3D_DRIVER_TYPE_HARDWARE,      // DriverType
        NULL,                          // Software
        D3D11_CREATE_DEVICE_SINGLETHREADED,
        feature_levels,
        ARRAYSIZE(feature_levels),
        D3D11_SDK_VERSION,
        device,
        &feature_level,
        context
    );
}

HRESULT initialize_dxgi_duplication(ID3D11Device* device, IDXGIOutputDuplication** dup, DXGI_OUTDUPL_DESC* dupl_desc) {
    IDXGIDevice* dxgi_device = NULL;
    IDXGIAdapter* adapter = NULL;
    IDXGIOutput* output = NULL;
    IDXGIOutput1* output1 = NULL;
    DXGI_OUTPUT_DESC output_desc;
    HRESULT hr;

    // Get DXGI device
    hr = device->QueryInterface(__uuidof(IDXGIDevice), (void**)&dxgi_device);
    if (FAILED(hr)) return hr;

    // Get adapter
    hr = dxgi_device->GetAdapter(&adapter);
    SAFE_RELEASE(dxgi_device);
    if (FAILED(hr)) return hr;

    // Get first output (primary monitor)
    hr = adapter->EnumOutputs(0, &output);
    SAFE_RELEASE(adapter);
    if (FAILED(hr)) return hr;

    // Get output description
    hr = output->GetDesc(&output_desc);
    if (FAILED(hr)) {
        SAFE_RELEASE(output);
        return hr;
    }

    // Get output1 interface for desktop duplication
    hr = output->QueryInterface(__uuidof(IDXGIOutput1), (void**)&output1);
    if (FAILED(hr)) {
        SAFE_RELEASE(output);
        return hr;
    }

    // Duplicate output
    hr = output1->DuplicateOutput(device, dup);
    SAFE_RELEASE(output1);
    SAFE_RELEASE(output);
    return hr;
}

// Media Foundation attribute GUIDs (in case headers don't define them)
#ifndef MF_MT_FRAME_RATE_NUMERATOR
static const GUID MF_MT_FRAME_RATE_NUMERATOR = { 0x6910d583, 0x4b8f, 0x401f, { 0xb3, 0xb9, 0xf6, 0x34, 0xd5, 0x6a, 0x30, 0x27 } };
static const GUID MF_MT_FRAME_RATE_DENOMINATOR = { 0xe2724bb8, 0xe676, 0x4806, { 0xb4, 0xb1, 0x6c, 0x0a, 0x65, 0x59, 0xf8, 0xda } };
#endif

HRESULT setup_h264_encoder(ID3D11Device* device, IMFTransform** encoder, int width, int height) {
    IMFMediaType* input_type = NULL;
    IMFMediaType* output_type = NULL;
    GUID format_bgra;
    HRESULT hr;

    // CoInitialize if not already done
    CoInitializeEx(NULL, COINIT_MULTITHREADED);

    // Startup Media Foundation
    hr = MFStartup(MF_VERSION, MFSTARTUP_LITE);
    if (FAILED(hr)) return hr;

    // Create H264 encoder MFT
    // CLSID_CMSH264EncoderMFT = {6CA50344-051A-4DED-9779-A43305165E35}
    GUID h264_encoder_clsid = { 0x6CA50344, 0x051A, 0x4DED, { 0x97, 0x79, 0xA4, 0x33, 0x05, 0x16, 0x5E, 0x35 } };

    hr = CoCreateInstance(h264_encoder_clsid, NULL, CLSCTX_INPROC_SERVER, __uuidof(IMFTransform), (void**)encoder);
    if (FAILED(hr)) return hr;

    // Create input media type (BGRA - what DXGI actually returns)
    hr = MFCreateMediaType(&input_type);
    if (FAILED(hr)) goto cleanup;

    hr = input_type->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);
    if (FAILED(hr)) goto cleanup;

    // Use BGRA format (what DXGI gives us)
    // 4CC code for BGRA
    format_bgra = { 0x34524742, 0x0000, 0x0010, { 0x80, 0x00, 0x00, 0xAA, 0x00, 0x38, 0x9B, 0x71 } };
    hr = input_type->SetGUID(MF_MT_SUBTYPE, format_bgra);
    if (FAILED(hr)) goto cleanup;

    hr = input_type->SetUINT32(MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive);
    if (FAILED(hr)) goto cleanup;

    hr = input_type->SetUINT32(MF_MT_FRAME_SIZE, ((UINT32)width << 16) | (UINT32)height);
    if (FAILED(hr)) goto cleanup;

    // Frame rate: 30000/1001 (29.97 fps, standard for video)
    hr = input_type->SetUINT32(MF_MT_FRAME_RATE_NUMERATOR, 30000);
    if (FAILED(hr)) goto cleanup;

    hr = input_type->SetUINT32(MF_MT_FRAME_RATE_DENOMINATOR, 1001);
    if (FAILED(hr)) goto cleanup;

    hr = input_type->SetUINT32(MF_MT_PIXEL_ASPECT_RATIO, (1 << 16) | 1);
    if (FAILED(hr)) goto cleanup;

    // Set input type on encoder
    hr = (*encoder)->SetInputType(0, input_type, 0);
    if (FAILED(hr)) goto cleanup;

    // Create output media type (H264)
    hr = MFCreateMediaType(&output_type);
    if (FAILED(hr)) goto cleanup;

    hr = output_type->SetGUID(MF_MT_MAJOR_TYPE, MFMediaType_Video);
    if (FAILED(hr)) goto cleanup;

    hr = output_type->SetGUID(MF_MT_SUBTYPE, MFVideoFormat_H264);
    if (FAILED(hr)) goto cleanup;

    // Bitrate: 2500 kbps
    hr = output_type->SetUINT32(MF_MT_AVG_BITRATE, 2500000);
    if (FAILED(hr)) goto cleanup;

    hr = output_type->SetUINT32(MF_MT_INTERLACE_MODE, MFVideoInterlace_Progressive);
    if (FAILED(hr)) goto cleanup;

    hr = output_type->SetUINT32(MF_MT_FRAME_SIZE, ((UINT32)width << 16) | (UINT32)height);
    if (FAILED(hr)) goto cleanup;

    hr = output_type->SetUINT32(MF_MT_FRAME_RATE_NUMERATOR, 30000);
    if (FAILED(hr)) goto cleanup;

    hr = output_type->SetUINT32(MF_MT_FRAME_RATE_DENOMINATOR, 1001);
    if (FAILED(hr)) goto cleanup;

    hr = output_type->SetUINT32(MF_MT_MPEG2_PROFILE, 1);
    if (FAILED(hr)) goto cleanup;

    hr = output_type->SetUINT32(MF_MT_MPEG2_LEVEL, 41);
    if (FAILED(hr)) goto cleanup;

    // Set output type on encoder
    hr = (*encoder)->SetOutputType(0, output_type, 0);
    if (FAILED(hr)) goto cleanup;

    // Start streaming
    hr = (*encoder)->ProcessMessage(MFT_MESSAGE_NOTIFY_BEGIN_STREAMING, 0);
    if (FAILED(hr)) goto cleanup;

    hr = (*encoder)->ProcessMessage(MFT_MESSAGE_COMMAND_FLUSH, 0);

cleanup:
    SAFE_RELEASE(input_type);
    SAFE_RELEASE(output_type);
    return hr;
}

H264Encoder* h264_init(void) {
    H264Encoder* enc = (H264Encoder*)malloc(sizeof(H264Encoder));
    if (!enc) return NULL;

    memset(enc, 0, sizeof(*enc));
    HRESULT hr;

    // Create Direct3D 11 device
    hr = create_d3d11_device(&enc->d3d_device, &enc->d3d_context);
    if (FAILED(hr)) {
        set_error(enc, "D3D11CreateDevice failed: 0x%08X", hr);
        goto error;
    }
    printf("[h264] Direct3D 11 device created\n");

    // Initialize DXGI Desktop Duplication
    hr = initialize_dxgi_duplication(enc->d3d_device, &enc->duplication, &enc->dupl_desc);
    if (FAILED(hr)) {
        set_error(enc, "DuplicateOutput failed: 0x%08X", hr);
        goto error;
    }

    enc->width = enc->dupl_desc.ModeDesc.Width;
    enc->height = enc->dupl_desc.ModeDesc.Height;
    printf("[h264] DXGI Desktop Duplication initialized: %dx%d\n", enc->width, enc->height);

    // Setup H264 encoder
    hr = setup_h264_encoder(enc->d3d_device, &enc->encoder, enc->width, enc->height);
    if (FAILED(hr)) {
        set_error(enc, "H264 encoder setup failed: 0x%08X", hr);
        goto error;
    }
    printf("[h264] H264 encoder initialized\n");

    enc->frame_count = 0;
    strcpy_s(enc->error_msg, sizeof(enc->error_msg), "");
    return enc;

error:
    SAFE_RELEASE(enc->duplication);
    SAFE_RELEASE(enc->d3d_context);
    SAFE_RELEASE(enc->d3d_device);
    printf("[h264] Initialization failed: %s\n", enc->error_msg);
    free(enc);
    return NULL;
}

void h264_get_dimensions(H264Encoder* enc, int* width, int* height) {
    if (enc) {
        *width = enc->width;
        *height = enc->height;
    }
}

uint8_t* h264_read_frame(H264Encoder* enc, int* out_len) {
    if (!enc || !enc->duplication || !enc->encoder) {
        *out_len = 0;
        return NULL;
    }

    HRESULT hr;
    IDXGIResource* desktop_resource = NULL;
    ID3D11Texture2D* desktop_texture = NULL;
    D3D11_MAPPED_SUBRESOURCE mapped;
    IMFSample* input_sample = NULL;
    IMFMediaBuffer* input_buffer = NULL;
    BYTE* input_data = NULL;
    MFT_OUTPUT_DATA_BUFFER output_data = {};
    DWORD output_status = 0;
    IMFSample* output_sample = NULL;
    IMFMediaBuffer* output_buffer = NULL;
    BYTE* output_ptr = NULL;
    DWORD output_len = 0;
    uint8_t* result = NULL;

    // Try to acquire next frame
    DXGI_OUTDUPL_FRAME_INFO frame_info;
    hr = enc->duplication->AcquireNextFrame(100, &frame_info, &desktop_resource);

    if (hr == DXGI_ERROR_WAIT_TIMEOUT) {
        *out_len = 0;
        return NULL;
    }

    if (FAILED(hr)) {
        if (enc->frame_count < 10) {
            printf("[dxgi] AcquireNextFrame frame %d: 0x%08X\n", enc->frame_count, hr);
        }
        set_error(enc, "AcquireNextFrame failed: 0x%08X", hr);
        *out_len = 0;
        return NULL;
    }

    if (enc->frame_count < 5) {
        printf("[dxgi] Frame %d acquired, size info: %dx%d\n", enc->frame_count, enc->width, enc->height);
    }

    // Get texture from resource
    hr = desktop_resource->QueryInterface(__uuidof(ID3D11Texture2D), (void**)&desktop_texture);
    SAFE_RELEASE(desktop_resource);
    if (FAILED(hr)) {
        set_error(enc, "QueryInterface failed: 0x%08X", hr);
        *out_len = 0;
        return NULL;
    }

    // Map texture for reading
    hr = enc->d3d_context->Map(desktop_texture, 0, D3D11_MAP_READ, 0, &mapped);
    if (FAILED(hr)) {
        set_error(enc, "Map failed: 0x%08X", hr);
        SAFE_RELEASE(desktop_texture);
        *out_len = 0;
        return NULL;
    }

    // Create input sample with BGRA data (DXGI format)
    int frame_size = enc->width * enc->height * 4; // 4 bytes per pixel for BGRA
    hr = MFCreateMemoryBuffer(frame_size, &input_buffer);
    if (FAILED(hr)) {
        set_error(enc, "MFCreateMemoryBuffer failed: 0x%08X", hr);
        goto frame_error;
    }

    hr = input_buffer->Lock(&input_data, NULL, NULL);
    if (FAILED(hr)) {
        set_error(enc, "Lock failed: 0x%08X", hr);
        goto frame_error;
    }

    // Copy frame data as-is (BGRA from DXGI)
    // TODO: Verify actual stride from DXGI (may include padding)
    memcpy(input_data, mapped.pData, frame_size);
    input_buffer->Unlock();

    // Set buffer length
    hr = input_buffer->SetCurrentLength(frame_size);
    if (FAILED(hr)) {
        set_error(enc, "SetCurrentLength failed: 0x%08X", hr);
        goto frame_error;
    }

    hr = MFCreateSample(&input_sample);
    if (FAILED(hr)) {
        set_error(enc, "MFCreateSample failed: 0x%08X", hr);
        goto frame_error;
    }

    hr = input_sample->AddBuffer(input_buffer);
    if (FAILED(hr)) {
        set_error(enc, "AddBuffer failed: 0x%08X", hr);
        goto frame_error;
    }

    input_sample->SetSampleTime(enc->frame_count * 333333); // ~30fps
    input_sample->SetSampleDuration(333333);

    // Process input
    hr = enc->encoder->ProcessInput(0, input_sample, 0);
    if (enc->frame_count < 5) {
        printf("[h264] ProcessInput frame %d: 0x%08X\n", enc->frame_count, hr);
    }
    if (FAILED(hr) && hr != MF_E_NOTACCEPTING) {
        set_error(enc, "ProcessInput failed: 0x%08X", hr);
        goto frame_error;
    }

    // Get output
    output_data.dwStreamID = 0;
    hr = enc->encoder->ProcessOutput(0, 1, &output_data, &output_status);

    if (enc->frame_count < 5) {
        printf("[h264] ProcessOutput frame %d: 0x%08X, sample=%p\n", enc->frame_count, hr, output_data.pSample);
    }

    if (hr == MF_E_TRANSFORM_NEED_MORE_INPUT) {
        // No output yet, that's ok
        *out_len = 0;
        goto frame_done;
    }

    if (FAILED(hr)) {
        set_error(enc, "ProcessOutput failed: 0x%08X", hr);
        goto frame_error;
    }

    // Extract output buffer
    if (output_data.pSample) {
        hr = output_data.pSample->GetBufferByIndex(0, &output_buffer);
        if (SUCCEEDED(hr)) {
            hr = output_buffer->Lock(&output_ptr, NULL, &output_len);
            if (SUCCEEDED(hr)) {
                result = (uint8_t*)malloc(output_len);
                if (result) {
                    memcpy(result, output_ptr, output_len);
                    *out_len = output_len;
                }
                output_buffer->Unlock();
            }
            SAFE_RELEASE(output_buffer);
        }
        SAFE_RELEASE(output_data.pSample);
    }

frame_done:
    enc->frame_count++;

frame_error:
    enc->d3d_context->Unmap(desktop_texture, 0);
    SAFE_RELEASE(desktop_texture);
    SAFE_RELEASE(input_sample);
    SAFE_RELEASE(input_buffer);

    return result;
}

void h264_free_frame(uint8_t* data) {
    if (data) free(data);
}

void h264_close(H264Encoder* enc) {
    if (!enc) return;

    if (enc->encoder) {
        enc->encoder->ProcessMessage(MFT_MESSAGE_NOTIFY_END_STREAMING, 0);
        SAFE_RELEASE(enc->encoder);
    }

    SAFE_RELEASE(enc->duplication);
    SAFE_RELEASE(enc->d3d_context);
    SAFE_RELEASE(enc->d3d_device);

    MFShutdown();

    free(enc);
}

const char* h264_get_error(H264Encoder* enc) {
    if (!enc) return "NULL encoder";
    return enc->error_msg[0] ? enc->error_msg : "No error";
}
