/*-
 * #%L
 * mastodon-ellipsoid-fitting
 * %%
 * Copyright (C) 2015 - 2022 Tobias Pietzsch, Jean-Yves Tinevez
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package org.mastodon.mamut.fitting.ellipsoid;

import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.util.LinAlgHelpers;

/**
 * This is an adaption of the algorithm described in <a href=
 * "http://www.geometrictools.com/Documentation/DistancePointEllipseEllipsoid.pdf">
 * "Distance from a Point to an Ellipse, an Ellipsoid, or a Hyperellipsoid" by
 * David Eberly</a> and available as a C++ implementation from
 * <a href="http://www.geometrictools.com">www.geometrictools.com</a> under the
 * <a href="http://www.boost.org/LICENSE_1_0.txt">Boost License</a>
 *
 * @author Tobias Pietzsch &lt;tobias.pietzsch@gmail.com&gt;
 */
public class DistPointHyperEllipsoid
{
	public static class Result
	{
		public double distance;
		public double[] closestPointCoords;
		public RealPoint closestPoint;
	}

	public static Result distPointHyperEllipsoid( final RealLocalizable point, final HyperEllipsoid hyperellipsoid )
	{
		final double[] p = new double[ point.numDimensions() ];
		point.localize( p );
		return distPointHyperEllipsoid( p, hyperellipsoid );
	}

	public static Result distPointHyperEllipsoid( final double[] point, final HyperEllipsoid hyperellipsoid )
	{
		final int n = point.length;

	    final Result result = new Result();

	    // Compute the coordinates of Y in the hyperellipsoid coordinate system.
	    final double[] diff = new double[ n ];
	    LinAlgHelpers.subtract( point, hyperellipsoid.getCenter(), diff );
	    final double[] y = new double[ n ];
	    LinAlgHelpers.mult( hyperellipsoid.getAxes(), diff, y );

	    // Compute the closest hyperellipsoid point in the axis-aligned
	    // coordinate system.
	    final double[] x = new double[ n ];
	    final double sqrDistance = SqrDistance(hyperellipsoid.getRadii(), y, x);
	    result.distance = Math.sqrt( sqrDistance );

	    // Convert back to the original coordinate system.
	    LinAlgHelpers.multT( hyperellipsoid.getAxes(), x, y );
	    LinAlgHelpers.add( y, hyperellipsoid.getCenter(), y );
	    result.closestPointCoords = y;
	    result.closestPoint = RealPoint.wrap( y );

	    return result;
	}

	private static double SqrDistance(
			final double[] e,
			final double[] y,
			final double[] x )
	{
		final int n = e.length;

		// Determine negations for y to the first octant.
		final boolean[] negate = new boolean[ n ];
		for ( int i = 0; i < n; ++i )
		{
			negate[ i ] = ( y[ i ] < 0 );
		}

		// Determine the axis order for decreasing extents.
		final double[] minuse = new double[ n ];
		final int[] permute = new int[ n ];
		for ( int i = 0; i < n; ++i )
		{
			minuse[ i ] = -e[ i ];
			permute[ i ] = i;
		}
		net.imglib2.util.Util.quicksort( minuse, permute, 0, n - 1 );

		final int[] invPermute = new int[ n ];
		for ( int i = 0; i < n; ++i )
		{
			invPermute[ permute[ i ] ] = i;
		}

		final double[] locE = new double[ n ];
		final double[] locY = new double[ n ];
		for ( int i = 0; i < n; ++i )
		{
			final int j = permute[ i ];
			locE[ i ] = e[ j ];
			locY[ i ] = Math.abs( y[ j ] );
		}

		final double[] locX = new double[ n ];
		final double sqrDistance = SqrDistanceSpecial( locE, locY, locX );

		// Restore the axis order and reflections.
		for ( int i = 0; i < n; ++i )
		{
			final int j = invPermute[ i ];
			x[ i ] = negate[ i ] ? -locX[ j ] : locX[ j ];
		}

		return sqrDistance;
	}

	/**
	 * The hyperellipsoid is sum_{d=0}^{N-1} (x[d]/e[d])^2 = 1 with the e[d]
	 * positive and nonincreasing: e[d] >= e[d+1] for all d. The query point is
	 * (y[0],...,y[N-1]) with y[d] >= 0 for all d. The function returns the
	 * squared distance from the query point to the hyperellipsoid. It also
	 * computes the hyperellipsoid point (x[0],...,x[N-1]) that is closest to
	 * (y[0],...,y[N-1]), where x[d] >= 0 for all d.
	 *
	 * @param e
	 *            axis lengths. e[d] >= e[d+1] for all d.
	 * @param y
	 *            query point. y[d] >= 0 for all d.
	 * @param x
	 *            hyperellipsoid point closest to y is stored here. (x[d] >= 0
	 *            for all d.)
	 * @return squared distance from the query point to the hyperellipsoid.
	 */
	private static double SqrDistanceSpecial(
			final double[] e,
			final double[] y,
			final double[] x )
	{
		final int n = e.length;
		double sqrDistance = 0;

		final double[] ePos = new double[ n ];
		final double[] yPos = new double[ n ];
		final double[] xPos = new double[ n ];
		int numPos = 0;
		for ( int i = 0; i < n; ++i )
		{
			if ( y[ i ] > 0 )
			{
				ePos[ numPos ] = e[ i ];
				yPos[ numPos ] = y[ i ];
				++numPos;
			}
			else
			{
				x[ i ] = 0;
			}
		}

		if ( y[ n - 1 ] > 0 )
		{
			sqrDistance = Bisector( numPos, ePos, yPos, xPos );
		}
		else
		// y[n-1] = 0
		{
			final double[] numer = new double[ n - 1 ];
			final double[] denom = new double[ n - 1 ];
			final double eNm1Sqr = e[ n - 1 ] * e[ n - 1 ];
			for ( int i = 0; i < numPos; ++i )
			{
				numer[ i ] = ePos[ i ] * yPos[ i ];
				denom[ i ] = ePos[ i ] * ePos[ i ] - eNm1Sqr;
			}

			boolean inSubHyperbox = true;
			for ( int i = 0; i < numPos; ++i )
			{
				if ( numer[ i ] >= denom[ i ] )
				{
					inSubHyperbox = false;
					break;
				}
			}

			boolean inSubHyperellipsoid = false;
			if ( inSubHyperbox )
			{
				// yPos[] is inside the axis-aligned bounding box of the
				// subhyperellipsoid. This intermediate test is designed to
				// guard against the division by zero when ePos[i] == e[n-1] for
				// some i.
				final double[] xde = new double[ n - 1 ];
				double discr = 1;
				for ( int i = 0; i < numPos; ++i )
				{
					xde[ i ] = numer[ i ] / denom[ i ];
					discr -= xde[ i ] * xde[ i ];
				}
				if ( discr > 0 )
				{
					// yPos[] is inside the subhyperellipsoid. The closest
					// hyperellipsoid point has x[n-1] > 0.
					sqrDistance = 0;
					for ( int i = 0; i < numPos; ++i )
					{
						xPos[ i ] = ePos[ i ] * xde[ i ];
						final double diff = xPos[ i ] - yPos[ i ];
						sqrDistance += diff * diff;
					}
					x[ n - 1 ] = e[ n - 1 ] * Math.sqrt( discr );
					sqrDistance += x[ n - 1 ] * x[ n - 1 ];
					inSubHyperellipsoid = true;
				}
			}

			if ( !inSubHyperellipsoid )
			{
				// yPos[] is outside the subhyperellipsoid. The closest
				// hyperellipsoid point has x[n-1] == 0 and is on the
				// domain-boundary hyperellipsoid.
				x[ n - 1 ] = 0;
				sqrDistance = Bisector( numPos, ePos, yPos, xPos );
			}
		}

		// Fill in those x[] values that were not zeroed out initially.
		numPos = 0;
		for ( int i = 0; i < n; ++i )
		{
			if ( y[ i ] > 0 )
			{
				x[ i ] = xPos[ numPos ];
				++numPos;
			}
		}

		return sqrDistance;
	}

	private static double Bisector(
			final int numComponents,
			final double[] e,
			final double[] y,
			final double[] x )
	{
		final int n = e.length;
		final double[] z = new double[ n ];
		double sumZSqr = 0;
		for ( int i = 0; i < numComponents; ++i )
		{
			z[ i ] = y[ i ] / e[ i ];
			sumZSqr += z[ i ] * z[ i ];
		}

		if ( sumZSqr == 1 )
		{
			// The point is on the hyperellipsoid.
			for ( int i = 0; i < numComponents; ++i )
			{
				x[ i ] = y[ i ];
			}
			return 0;
		}

		final double emin = e[ numComponents - 1 ];
		final double[] pSqr = new double[ n ];
		final double[] numerator = new double[ n ];
		for ( int i = 0; i < numComponents; ++i )
		{
			final double p = e[ i ] / emin;
			pSqr[ i ] = p * p;
			numerator[ i ] = pSqr[ i ] * z[ i ];
		}

		double s = 0, smin = z[ numComponents - 1 ] - 1, smax;
		if ( sumZSqr < 1 )
		{
			// The point is strictly inside the hyperellipsoid.
			smax = 0;
		}
		else
		{
			// The point is strictly outside the hyperellipsoid.
			smax = LengthRobust( numerator ) - 1;
		}

		final double jmax = GetMaxBisections();
		for ( int j = 0; j < jmax; ++j )
		{
			s = ( smin + smax ) * 0.5;
			if ( s == smin || s == smax )
			{
				break;
			}

			double g = -1;
			for ( int i = 0; i < numComponents; ++i )
			{
				final double ratio = numerator[ i ] / ( s + pSqr[ i ] );
				g += ratio * ratio;
			}

			if ( g > 0 )
			{
				smin = s;
			}
			else if ( g < 0 )
			{
				smax = s;
			}
			else
			{
				break;
			}
		}

		double sqrDistance = 0;
		for ( int i = 0; i < numComponents; ++i )
		{
			x[ i ] = pSqr[ i ] * y[ i ] / ( s + pSqr[ i ] );
			final double diff = x[ i ] - y[ i ];
			sqrDistance += diff * diff;
		}
		return sqrDistance;
	}

	private static int GetMaxBisections()
	{
		final int doubleDigits = 52;
		final int doubleMinExponent = -1021;
		return 3 + doubleDigits - doubleMinExponent;
	}

	private static double LengthRobust( final double[] v )
	{
		return LinAlgHelpers.length( v );
	}
}
