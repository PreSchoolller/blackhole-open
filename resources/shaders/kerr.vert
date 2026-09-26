#version 450

// 克尔管线全屏四边形：NPGS 着色器在 frag 内经 gl_FragCoord + iInverseCamRot/iFovRadians
// 自行生成射线，顶点不传相机数据（无 push constant）；outUV 供 kerr_copy 采样历史图。
layout(location = 0) out vec2 outUV;

void main()
{
    vec2 uv = vec2(float((gl_VertexIndex << 1) & 2), float(gl_VertexIndex & 2));
    outUV = uv;
    gl_Position = vec4(uv * 2.0 - 1.0, 0.0, 1.0);
}
