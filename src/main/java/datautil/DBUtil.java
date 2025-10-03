package datautil;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;

public class DBUtil {
	
	public static void executeUpdate(String sql, Object... params) {
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			// Parameter setzen
			for (int i = 0; i < params.length; i++) {
				pstmt.setObject(i + 1, params[i]);
			}

			pstmt.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}

	public static <T> T getValueFromSQL(String sql, Class<T> clazz, Object... params) {
		T result = null;
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			// Parameter setzen
			for (int i = 0; i < params.length; i++) {
				pstmt.setObject(i + 1, params[i]);
			}

			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					result = rs.getObject(sql.split(" ")[1], clazz);
				}
				Statement stmt = rs.getStatement();
				stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return result;
	}

	public static OffsetDateTime getDateFromSQL(String sql, Object... params) {
		OffsetDateTime a = null;
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			// Parameter setzen
			for (int i = 0; i < params.length; i++) {
				pstmt.setObject(i + 1, params[i]);
			}

			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					a = rs.getObject(sql.split(" ")[1], OffsetDateTime.class);
				}
				Statement stmt = rs.getStatement();
				rs.close();
				stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return a;
	}

	public static <E> ArrayList<E> getArrayListFromSQL(String sql, Class<E> clazz, Object... params) {
		ArrayList<E> result = new ArrayList<>();
		try (PreparedStatement pstmt = Connection.getConnection().prepareStatement(sql)) {
			// Parameter setzen
			for (int i = 0; i < params.length; i++) {
				pstmt.setObject(i + 1, params[i]);
			}

			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					result.add(rs.getObject(sql.split(" ")[1], clazz));
				}
				Statement stmt = rs.getStatement();
				rs.close();
				stmt.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return result;
	}

}
