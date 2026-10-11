/*
 * Copyright (C) 2026 the Yamagi Quake II contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * See the GNU General Public License for more details.
 *
 * ETC2 / EAC texture compression (GLES3 and GL 4.3+).
 *
 * The encoder emits the ETC1 subset of ETC2 RGB8 for color (which is
 * also valid ETC1 for the GLES1 renderer) and EAC blocks for alpha.
 * Blocks are 4x4 texels, 8 bytes (RGB8) or 16 bytes (RGBA8/EAC) per
 * block; edge blocks are padded by clamping to the image edge.
 *
 * Layout facts (verified against an independent decoder):
 *  - 5 bit bases are bit replicated on decode: (v << 3) | (v >> 2),
 *    4 bit bases scale with * 17,
 *  - pixel (x, y) index position is pos = x * 4 + y (column major),
 *  - color index planes: value bit 1 at bit 16 + pos, bit 0 at bit
 *    pos of the low 32 bits of the block,
 *  - EAC alpha decodes as clamp(base + modifier * multiplier, 0, 255)
 *    with 3 bit indices at bits 45 - 3 * pos of the 48 bit index
 *    field.
 */

#include "../ref_shared.h"

#include <string.h>

/* intensity modifier sets for the ETC1 subset */
static const int etc_modifiers[8][4] = {
	{ 2, 8, -2, -8 },
	{ 5, 17, -5, -17 },
	{ 9, 29, -9, -29 },
	{ 13, 42, -13, -42 },
	{ 18, 60, -18, -60 },
	{ 24, 80, -24, -80 },
	{ 33, 106, -33, -106 },
	{ 47, 183, -47, -183 }
};

/* biggest absolute modifier per table, used to derive multipliers */
static const int etc_maxmod[8] = { 8, 17, 29, 42, 60, 80, 106, 183 };

/* alpha modifier sets for EAC */
static const int eac_modifiers[16][8] = {
	{ -3, -6, -9, -15, 2, 5, 8, 14 },
	{ -3, -7, -10, -13, 2, 6, 9, 12 },
	{ -2, -5, -8, -13, 1, 4, 7, 12 },
	{ -2, -4, -6, -13, 1, 3, 5, 12 },
	{ -3, -6, -8, -12, 2, 5, 7, 11 },
	{ -3, -7, -9, -11, 2, 6, 8, 10 },
	{ -4, -7, -8, -11, 3, 6, 7, 10 },
	{ -3, -5, -8, -11, 2, 4, 7, 10 },
	{ -2, -6, -8, -10, 1, 5, 7, 9 },
	{ -2, -5, -8, -10, 1, 4, 7, 9 },
	{ -2, -4, -8, -10, 1, 3, 7, 9 },
	{ -2, -5, -7, -10, 1, 4, 6, 9 },
	{ -3, -4, -7, -10, 2, 3, 6, 9 },
	{ -1, -2, -3, -10, 0, 1, 2, 9 },
	{ -4, -6, -8, -9, 3, 5, 7, 8 },
	{ -3, -5, -7, -9, 2, 4, 6, 8 }
};

static const int eac_maxmod[16] = {
	15, 13, 13, 13, 12, 11, 11, 11, 10, 10, 10, 10, 10, 10, 9, 9
};

static inline int
ETC2_Clamp(int v, int lo, int hi)
{
	return (v < lo) ? lo : ((v > hi) ? hi : v);
}

static inline unsigned int
ETC2_Pixel(const unsigned *data, int width, int height, int x, int y)
{
	if (x >= width)
	{
		x = width - 1;
	}

	if (y >= height)
	{
		y = height - 1;
	}

	return data[y * width + x];
}

/*
 * Encode one 4x4 color block in the ETC1 subset of ETC2 RGB8.
 */
static void
ETC2_EncodeBlockRGB(const unsigned *data, int width, int height,
		int bx, int by, unsigned char out[8])
{
	unsigned char r[16], g[16], b[16];
	unsigned long long best_word = 0;
	int best_err = 0x7FFFFFFF;
	int flip, mode;

	for (int x = 0; x < 4; x++)
	{
		for (int y = 0; y < 4; y++)
		{
			int i = x * 4 + y;
			unsigned int p = ETC2_Pixel(data, width, height, bx * 4 + x, by * 4 + y);

			r[i] = (unsigned char)(p & 0xFF);
			g[i] = (unsigned char)((p >> 8) & 0xFF);
			b[i] = (unsigned char)((p >> 16) & 0xFF);
		}
	}

	for (flip = 0; flip < 2; flip++)
	{
		for (mode = 0; mode < 2; mode++) /* 0 = individual, 1 = differential */
		{
			int qbase[2][3];
			int tsup[2][3], ntsup[2];
			int ok = 1;

			/* per subblock average colors and table shortlist */
			for (int s = 0; s < 2; s++)
			{
				int sum[3] = { 0, 0, 0 };
				int lumsq = 0;
				int lumsum = 0;
				int n = 0;

				for (int i = 0; i < 16; i++)
				{
					int x = i / 4, y = i % 4;
					int sub = flip ? (y / 2) : (x / 2);
					int lum;

					if (sub != s)
					{
						continue;
					}

					sum[0] += r[i];
					sum[1] += g[i];
					sum[2] += b[i];
					lum = (r[i] + g[i] + b[i]) / 3;
					lumsum += lum;
					lumsq += lum * lum;
					n++;
				}

				for (int c = 0; c < 3; c++)
				{
					int avg = (sum[c] + n / 2) / n;

					if (mode == 0)
					{
						qbase[s][c] = ETC2_Clamp((avg + 8) / 17, 0, 15);
					}
					else
					{
						qbase[s][c] = ETC2_Clamp((avg + 4) / 8, 0, 31);
					}
				}

				/* tables around the one whose modifier range fits
				 * the subblock variance */
				{
					int mean = lumsum / n;
					int var = lumsq / n - mean * mean;
					int target = 0;

					/* approx 1.5 sigma without floats */
					while ((target * target) < var)
					{
						target++;
					}

					target += target / 2;

					int t_est = 0;

					while ((t_est < 7) && (etc_maxmod[t_est] < target))
					{
						t_est++;
					}

					ntsup[s] = 0;

					for (int t = t_est - 1; t <= t_est + 1; t++)
					{
						if ((t >= 0) && (t <= 7))
						{
							tsup[s][ntsup[s]++] = t;
						}
					}
				}
			}

			if (mode == 1)
			{
				for (int c = 0; c < 3; c++)
				{
					int d = qbase[1][c] - qbase[0][c];

					if ((d < -4) || (d > 3))
					{
						ok = 0;
						break;
					}
				}

				if (!ok)
				{
					continue;
				}
			}

			/* try the quantized average and its neighbors */
			for (int shift = -1; shift <= 1; shift++)
			{
				int base[2][3];
				int bc[2][3];

				for (int s = 0; s < 2; s++)
				{
					for (int c = 0; c < 3; c++)
					{
						base[s][c] = ETC2_Clamp(qbase[s][c] + shift,
								0, (mode == 0) ? 15 : 31);

						if (mode == 0)
						{
							bc[s][c] = base[s][c] * 17;
						}
					}
				}

				if (mode == 1)
				{
					for (int c = 0; c < 3; c++)
					{
						int d = base[1][c] - base[0][c];

						if ((d < -4) || (d > 3))
						{
							ok = 0;
							break;
						}

						{
							int b5 = base[0][c];

							bc[0][c] = (b5 << 3) | (b5 >> 2);
							b5 = base[1][c];
							bc[1][c] = (b5 << 3) | (b5 >> 2);
						}
					}

					if (!ok)
					{
						continue;
					}
				}

				/* pick table + indices for each subblock */
				{
					int total_err = 0;
					int tbl[2] = { 0, 0 };
					int idx[2][16];
					unsigned long long word = 0;

					for (int s = 0; s < 2; s++)
					{
						int best_sub = 0x7FFFFFFF;

						for (int ti = 0; ti < ntsup[s]; ti++)
						{
							int t = tsup[s][ti];
							int err = 0;
							int ii[16];

							for (int i = 0; i < 16; i++)
							{
								int x = i / 4, y = i % 4;
								int sub = flip ? (y / 2) : (x / 2);
								int beste = 0x7FFFFFFF;
								int besti = 0;

								ii[i] = 0;

								if (sub != s)
								{
									continue;
								}

								for (int k = 0; k < 4; k++)
								{
									int m = etc_modifiers[t][k];
									int rr = ETC2_Clamp(bc[s][0] + m, 0, 255);
									int gg = ETC2_Clamp(bc[s][1] + m, 0, 255);
									int bb = ETC2_Clamp(bc[s][2] + m, 0, 255);
									int e = (rr - r[i]) * (rr - r[i]) +
										(gg - g[i]) * (gg - g[i]) +
										(bb - b[i]) * (bb - b[i]);

									if (e < beste)
									{
										beste = e;
										besti = k;
									}
								}

								ii[i] = besti;
								err += beste;

								if (err >= best_sub)
								{
									break;
								}
							}

							if (err < best_sub)
							{
								best_sub = err;
								tbl[s] = t;
								memcpy(idx[s], ii, sizeof(ii));
							}
						}

						total_err += best_sub;

						if (total_err >= best_err)
						{
							break;
						}
					}

					if (total_err < best_err)
					{
						best_err = total_err;

						if (mode == 1)
						{
							int dr = base[1][0] - base[0][0];
							int dg = base[1][1] - base[0][1];
							int db = base[1][2] - base[0][2];

							word |= (unsigned long long)base[0][0] << 59;
							word |= (unsigned long long)(dr & 7) << 56;
							word |= (unsigned long long)base[0][1] << 51;
							word |= (unsigned long long)(dg & 7) << 48;
							word |= (unsigned long long)base[0][2] << 43;
							word |= (unsigned long long)(db & 7) << 40;
							word |= 1ULL << 33;
						}
						else
						{
							word |= (unsigned long long)base[0][0] << 60;
							word |= (unsigned long long)base[1][0] << 56;
							word |= (unsigned long long)base[0][1] << 52;
							word |= (unsigned long long)base[1][1] << 48;
							word |= (unsigned long long)base[0][2] << 44;
							word |= (unsigned long long)base[1][2] << 40;
						}

						word |= (unsigned long long)tbl[0] << 37;
						word |= (unsigned long long)tbl[1] << 34;

						if (flip)
						{
							word |= 1ULL << 32;
						}

						for (int i = 0; i < 16; i++)
						{
							int x = i / 4, y = i % 4;
							int sub = flip ? (y / 2) : (x / 2);
							int v = idx[sub][i];
							int pos = x * 4 + y;

							if (v & 2)
							{
								word |= 1ULL << (16 + pos);
							}

							if (v & 1)
							{
								word |= 1ULL << pos;
							}
						}

						best_word = word;
					}
				}
			}
		}
	}

	for (int i = 0; i < 8; i++)
	{
		out[i] = (unsigned char)((best_word >> (56 - 8 * i)) & 0xFF);
	}
}

/*
 * Encode one 4x4 EAC alpha block.
 */
static void
ETC2_EncodeBlockAlpha(const unsigned *data, int width, int height,
		int bx, int by, unsigned char out[8])
{
	int a[16];
	int hi = 0, lo = 255;
	int avg = 0;

	for (int x = 0; x < 4; x++)
	{
		for (int y = 0; y < 4; y++)
		{
			int i = x * 4 + y;
			unsigned int p = ETC2_Pixel(data, width, height, bx * 4 + x, by * 4 + y);

			a[i] = (int)((p >> 24) & 0xFF);

			if (a[i] > hi)
			{
				hi = a[i];
			}

			if (a[i] < lo)
			{
				lo = a[i];
			}

			avg += a[i];
		}
	}

	avg = (avg + 8) / 16;

	/* flat blocks are exact and cheap */
	if (hi == lo)
	{
		int base = (a[0] <= 252) ? (a[0] + 3) : (a[0] - 2);
		int k = (a[0] <= 252) ? 0 : 4;

		out[0] = (unsigned char)base;
		out[1] = 0x10; /* multiplier 1, table 0 */
		memset(out + 2, 0, 6);

		if (k)
		{
			/* all pixels index 4 (+2) */
			unsigned long long idxw = 0;

			for (int i = 0; i < 16; i++)
			{
				int pos = (i / 4) * 4 + (i % 4);

				idxw |= (unsigned long long)4 << (45 - 3 * pos);
			}

			for (int i = 0; i < 6; i++)
			{
				out[2 + i] = (unsigned char)((idxw >> (40 - 8 * i)) & 0xFF);
			}
		}

		return;
	}

	/* rank all tables with a per table multiplier derived from the
	 * block range, then refine the best ones */
	{
		int range = hi - lo;
		int mid = lo + range / 2;
		int rank_t[16], rank_mult[16];
		int rank_err[16];

		for (int t = 0; t < 16; t++)
		{
			int mult = (range + eac_maxmod[t]) / (2 * eac_maxmod[t]);

			if (mult < 1)
			{
				mult = 1;
			}

			if (mult > 15)
			{
				mult = 15;
			}

			rank_t[t] = t;
			rank_mult[t] = mult;
			rank_err[t] = 0;

			for (int i = 0; i < 16; i++)
			{
				int beste = 0x7FFFFFFF;

				for (int k = 0; k < 8; k++)
				{
					int v = ETC2_Clamp(mid + eac_modifiers[t][k] * mult, 0, 255);
					int e = (v - a[i]) * (v - a[i]);

					if (e < beste)
					{
						beste = e;
					}
				}

				rank_err[t] += beste;
			}
		}

		/* selection sort the first 3 */
		for (int i = 0; i < 3; i++)
		{
			int best = i;

			for (int j = i + 1; j < 16; j++)
			{
				if (rank_err[j] < rank_err[best])
				{
					best = j;
				}
			}

			if (best != i)
			{
				int te = rank_err[i], tt = rank_t[i], tm = rank_mult[i];

				rank_err[i] = rank_err[best];
				rank_t[i] = rank_t[best];
				rank_mult[i] = rank_mult[best];
				rank_err[best] = te;
				rank_t[best] = tt;
				rank_mult[best] = tm;
			}
		}

		/* refine */
		{
			int best_err = 0x7FFFFFFF;
			int best_mult = 1, best_tbl = 0, best_base = avg;
			int best_idx[16];

			memset(best_idx, 0, sizeof(best_idx));

			for (int ti = 0; ti < 3; ti++)
			{
				int t = rank_t[ti];
				int m0 = rank_mult[ti];
				int m_cands[4];
				int nm = 0;

				m_cands[nm++] = 1;
				m_cands[nm++] = m0;

				if ((m0 > 1) && (nm < 4))
				{
					m_cands[nm++] = m0 - 1;
				}

				if ((m0 < 15) && (nm < 4))
				{
					m_cands[nm++] = m0 + 1;
				}

				for (int bi = 0; bi < 5; bi++)
				{
					int b_cands[5] = {
						avg,
						ETC2_Clamp(avg - 1, 0, 255), ETC2_Clamp(avg + 1, 0, 255),
						ETC2_Clamp(avg - 2, 0, 255), ETC2_Clamp(avg + 2, 0, 255)
					};
					int base = b_cands[bi];

					for (int mi = 0; mi < nm; mi++)
					{
						int mult = m_cands[mi];
						int err = 0;
						int ii[16];

						for (int i = 0; i < 16; i++)
						{
							int beste = 0x7FFFFFFF;
							int besti = 0;

							for (int k = 0; k < 8; k++)
							{
								int v = ETC2_Clamp(base + eac_modifiers[t][k] * mult, 0, 255);
								int e = (v - a[i]) * (v - a[i]);

								if (e < beste)
								{
									beste = e;
									besti = k;
								}
							}

							ii[i] = besti;
							err += beste;
						}

						if (err < best_err)
						{
							best_err = err;
							best_mult = mult;
							best_tbl = t;
							best_base = base;
							memcpy(best_idx, ii, sizeof(ii));
						}
					}
				}
			}

			out[0] = (unsigned char)best_base;
			out[1] = (unsigned char)(((best_mult & 15) << 4) | (best_tbl & 15));

			{
				unsigned long long idxw = 0;

				for (int i = 0; i < 16; i++)
				{
					int x = i / 4, y = i % 4;
					int pos = x * 4 + y;

					idxw |= (unsigned long long)(best_idx[i] & 7) << (45 - 3 * pos);
				}

				for (int i = 0; i < 6; i++)
				{
					out[2 + i] = (unsigned char)((idxw >> (40 - 8 * i)) & 0xFF);
				}
			}
		}
	}
}

size_t
ETC2_EncodedSizeRGB(int width, int height)
{
	return (size_t)(((width + 3) / 4) * ((height + 3) / 4) * 8);
}

size_t
ETC2_EncodedSizeRGBA(int width, int height)
{
	return (size_t)(((width + 3) / 4) * ((height + 3) / 4) * 16);
}

void
ETC2_EncodeRGB(const unsigned *data, int width, int height, unsigned char *out)
{
	int bw = (width + 3) / 4, bh = (height + 3) / 4;

	for (int by = 0; by < bh; by++)
	{
		for (int bx = 0; bx < bw; bx++)
		{
			ETC2_EncodeBlockRGB(data, width, height, bx, by,
					out + (by * bw + bx) * 8);
		}
	}
}

void
ETC2_EncodeRGBA(const unsigned *data, int width, int height, unsigned char *out)
{
	int bw = (width + 3) / 4, bh = (height + 3) / 4;

	for (int by = 0; by < bh; by++)
	{
		for (int bx = 0; bx < bw; bx++)
		{
			unsigned char *blk = out + (by * bw + bx) * 16;

			ETC2_EncodeBlockAlpha(data, width, height, bx, by, blk);
			ETC2_EncodeBlockRGB(data, width, height, bx, by, blk + 8);
		}
	}
}

/*
 * Box filter downsample to ceil(width / 2) x ceil(height / 2),
 * used to build CPU side mip chains for compressed uploads.
 */
void
ETC2_Downsample(const unsigned *src, int width, int height, unsigned *dst)
{
	int dw = (width + 1) / 2, dh = (height + 1) / 2;

	for (int y = 0; y < dh; y++)
	{
		for (int x = 0; x < dw; x++)
		{
			int sx[2], sy[2], n = 0;
			unsigned int sum[4] = { 0, 0, 0, 0 };

			sx[0] = x * 2;
			sx[1] = (x * 2 + 1 < width) ? x * 2 + 1 : -1;
			sy[0] = y * 2;
			sy[1] = (y * 2 + 1 < height) ? y * 2 + 1 : -1;

			for (int j = 0; j < 2; j++)
			{
				for (int i = 0; i < 2; i++)
				{
					unsigned int p;

					if ((sx[i] < 0) || (sy[j] < 0))
					{
						continue;
					}

					p = src[sy[j] * width + sx[i]];
					sum[0] += p & 0xFF;
					sum[1] += (p >> 8) & 0xFF;
					sum[2] += (p >> 16) & 0xFF;
					sum[3] += (p >> 24) & 0xFF;
					n++;
				}
			}

			dst[y * dw + x] = (unsigned)((sum[0] / n) |
					((sum[1] / n) << 8) |
					((sum[2] / n) << 16) |
					((sum[3] / n) << 24));
		}
	}
}
